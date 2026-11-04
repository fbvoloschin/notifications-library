package dev.notifications;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

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

    NotificationClient(Map<String, Route> routes, Executor executor, RetryPolicy retryPolicy, RetryWait retryWait) {
        this.routes = routes;
        this.executor = executor;
        this.retryPolicy = retryPolicy;
        this.retryWait = retryWait;
    }

    public static NotificationClientBuilder builder() {
        return new NotificationClientBuilder();
    }

    public ProviderAcceptance send(SendRequest request) {
        return send(request, UUID.randomUUID());
    }

    /** Enqueues the synchronous flow. Cancelling the result does not cancel the underlying send. */
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

    private static SendException admissionFailure(SendRequest request, UUID correlationId) {
        var channel = channelOf(request);
        return unresolved(EXECUTION_REJECTED, correlationId, channel == null ? null : channel.id());
    }

    private static Channel<?> channelOf(SendRequest request) {
        try {
            return request == null ? null : request.notification().channel();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ProviderAcceptance send(SendRequest request, UUID correlationId) {
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
}
