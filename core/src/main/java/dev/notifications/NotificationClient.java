package dev.notifications;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import static dev.notifications.SendException.Reason.*;

/** Sends notifications through an immutable registry of configured providers. */
public final class NotificationClient {
    record RegisteredProvider(NotificationProvider<Notification> sender, Runnable validateConfiguration) { }

    record Route(Channel<?> channel, NotificationValidator<Notification> validator,
                 Map<String, RegisteredProvider> providers, String defaultProvider) { }

    private final Map<String, Route> routes;
    private final Executor executor;
    private final RetryPolicy retryPolicy;
    private final RetryWait retryWait;
    private final Clock clock;
    private final List<Consumer<SendEvent>> listeners;

    NotificationClient(Map<String, Route> routes, Executor executor, RetryPolicy retryPolicy, RetryWait retryWait,
                       Clock clock, List<Consumer<SendEvent>> listeners) {
        this.routes = routes;
        this.executor = executor;
        this.retryPolicy = retryPolicy;
        this.retryWait = retryWait;
        this.clock = clock;
        this.listeners = listeners;
    }

    public static NotificationClientBuilder builder() {
        return new NotificationClientBuilder();
    }

    /** Sends a request synchronously through its selected or default provider. */
    public ProviderAcceptance send(SendRequest request) {
        return send(request, UUID.randomUUID());
    }

    /**
     * Enqueues the synchronous flow on the configured host executor.
     * Cancelling the result does not cancel the underlying send.
     */
    public CompletableFuture<ProviderAcceptance> sendAsync(SendRequest request) {
        var correlationId = UUID.randomUUID();
        if (executor == null) {
            return CompletableFuture.failedFuture(admissionFailure(request, correlationId));
        }

        // Keep the observer separate from the runnable so early cancellation cannot skip the send.
        var result = new CompletableFuture<ProviderAcceptance>();
        try {
            executor.execute(() -> {
                try {
                    result.complete(send(request, correlationId));
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException ignored) {
            result.completeExceptionally(admissionFailure(request, correlationId));
        }
        return result;
    }

    /** Returns one independent asynchronous result per request, in input order. */
    public List<CompletableFuture<ProviderAcceptance>> sendAll(List<SendRequest> requests) {
        return requests.stream().map(this::sendAsync).toList();
    }

    private SendException admissionFailure(SendRequest request, UUID correlationId) {
        var channel = channelOf(request);
        var failure = unresolved(EXECUTION_REJECTED, correlationId, channel == null ? null : channel.id());
        publishFailure(failure);
        return failure;
    }

    private static Channel<?> channelOf(SendRequest request) {
        try {
            return request == null ? null : request.notification().channel();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ProviderAcceptance send(SendRequest request, UUID correlationId) {
        ProviderAcceptance acceptance;
        try {
            acceptance = deliver(request, correlationId);
        } catch (SendException failure) {
            publishFailure(failure);
            throw failure;
        }
        publish(SendEvent.Type.ACCEPTED, correlationId, acceptance.channel(), acceptance.provider(),
                acceptance.attempts(), null);
        return acceptance;
    }

    private ProviderAcceptance deliver(SendRequest request, UUID correlationId) {
        if (request == null) {
            throw unresolved(INVALID_NOTIFICATION, correlationId, null);
        }
        var channel = channelOf(request);
        if (channel == null) {
            throw unresolved(UNKNOWN_CHANNEL, correlationId, null);
        }
        var notification = request.notification();
        var route = routes.get(channel.id());
        if (route == null) {
            throw unresolved(UNKNOWN_CHANNEL, correlationId, channel.id());
        }
        if (!route.channel().equals(channel) || !route.channel().type().isInstance(notification)) {
            throw unresolved(CHANNEL_TYPE_MISMATCH, correlationId, channel.id());
        }

        List<String> violations;
        try {
            violations = List.copyOf(route.validator().validate(notification));
        } catch (RuntimeException ignored) {
            throw unresolved(UNEXPECTED_VALIDATION, correlationId, channel.id());
        }
        if (!violations.isEmpty()) {
            throw new SendException(INVALID_NOTIFICATION, correlationId, channel.id(), null, 0, violations);
        }

        var providerName = request.provider().orElse(route.defaultProvider());
        if (providerName == null) {
            throw unresolved(MISSING_DEFAULT, correlationId, channel.id());
        }
        var provider = route.providers().get(providerName);
        if (provider == null) {
            throw unresolved(UNKNOWN_PROVIDER, correlationId, channel.id());
        }

        return sendToProvider(notification, channel.id(), providerName, provider.sender(), correlationId);
    }

    private ProviderAcceptance sendToProvider(Notification notification, String channel, String providerName,
                                              NotificationProvider<Notification> provider, UUID correlationId) {
        var backoff = retryPolicy.initialDelay();
        for (int attempt = 1; ; attempt++) {
            try {
                var messageId = Objects.requireNonNull(provider.send(notification));
                return new ProviderAcceptance(channel, providerName, attempt, messageId, correlationId);
            } catch (ProviderFailure classified) {
                if (classified.kind() != ProviderFailure.Kind.TRANSIENT || attempt >= retryPolicy.maxAttempts()) {
                    throw new SendException(classified.kind().reason, correlationId, channel, providerName, attempt, List.of());
                }
                var minimum = classified.retryAfter();
                awaitRetry(backoff.compareTo(minimum) >= 0 ? backoff : minimum,
                        correlationId, channel, providerName, attempt);
                backoff = retryPolicy.nextBackoff(backoff);
                publish(SendEvent.Type.RETRYING, correlationId, channel, providerName, attempt, null);
            } catch (RuntimeException ignored) {
                throw new SendException(UNEXPECTED_PROVIDER_FAILURE, correlationId, channel, providerName, attempt, List.of());
            }
        }
    }

    private void awaitRetry(Duration delay, UUID id, String channel, String provider, int attempts) {
        try {
            retryWait.await(delay);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            throw new SendException(INTERRUPTED, id, channel, provider, attempts, List.of());
        } catch (RuntimeException ignored) {
            throw new SendException(UNEXPECTED_RETRY_WAIT, id, channel, provider, attempts, List.of());
        }
    }

    private static SendException unresolved(SendException.Reason reason, UUID id, String channel) {
        return new SendException(reason, id, channel, null, 0, List.of());
    }

    private void publishFailure(SendException failure) {
        publish(SendEvent.Type.FAILED, failure.correlationId(), failure.channel(), failure.provider().orElse(null),
                failure.attempts(), failure.reason());
    }

    private void publish(SendEvent.Type type, UUID id, String channel, String provider, int attempts,
                         SendException.Reason reason) {
        if (listeners.isEmpty()) {
            return;
        }
        var event = new SendEvent(type, id, clock.instant(), channel, Optional.ofNullable(provider),
                attempts, Optional.ofNullable(reason));
        for (var listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException ignored) {
                // A listener cannot change the outcome or prevent other listeners from being notified.
            }
        }
    }
}
