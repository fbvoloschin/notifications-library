package dev.notifications;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Registers typed channels and named providers in Java. */
public final class NotificationClientBuilder {
    private final Map<String, NotificationClient.Route> routes = new HashMap<>();
    private Executor executor;
    private RetryPolicy retryPolicy = RetryPolicy.defaults();
    private RetryWait retryWait = Thread::sleep;
    private Clock clock = Clock.systemUTC();
    private final List<Consumer<SendEvent>> listeners = new ArrayList<>();

    /**
     * Configures the host-owned executor used by asynchronous sends and batches.
     * It must dispatch off the caller thread without waiting for completion and reject via
     * {@link java.util.concurrent.RejectedExecutionException}, not run inline or silently discard.
     */
    public NotificationClientBuilder executor(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "Executor is required");
        return this;
    }

    /** Configures retries; the default makes a single provider attempt. */
    public NotificationClientBuilder retryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "Retry policy is required");
        return this;
    }

    /** Replaces the default interruptible thread sleep, e.g. for deterministic tests. */
    public NotificationClientBuilder retryWait(RetryWait retryWait) {
        this.retryWait = Objects.requireNonNull(retryWait, "Retry wait is required");
        return this;
    }

    /** Configures the clock used for event timestamps; defaults to UTC system time. */
    public NotificationClientBuilder clock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        return this;
    }

    /**
     * Adds a synchronous observer in registration order. Admission errors run on the caller thread.
     * Notifications from different sends may arrive concurrently, so listeners must be thread-safe and brief.
     */
    public NotificationClientBuilder addListener(Consumer<SendEvent> listener) {
        listeners.add(Objects.requireNonNull(listener, "Listener is required"));
        return this;
    }

    /** Registers the notification type and validator used for a channel. */
    public <N extends Notification> NotificationClientBuilder registerChannel(
            Channel<N> channel, NotificationValidator<N> validator) {
        Objects.requireNonNull(validator, "Validator is required");
        NotificationValidator<Notification> checked = notification -> validator.validate(channel.type().cast(notification));
        var route = new NotificationClient.Route(channel, checked, Map.of(), null);
        if (routes.putIfAbsent(channel.id(), route) != null) {
            throw new IllegalArgumentException("Channel is already registered");
        }
        return this;
    }

    /**
     * Registers a named provider for a previously registered channel.
     * Provider names are case-sensitive and unique within the channel; at most one provider may be its default.
     */
    public <N extends Notification> NotificationClientBuilder registerProvider(
            Channel<N> channel, String name, NotificationProvider<N> provider, boolean useAsDefault) {
        Objects.requireNonNull(provider, "Provider is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Provider name is required");
        }
        var route = routes.get(channel.id());
        if (route == null || !route.channel().equals(channel)) {
            throw new IllegalArgumentException("Provider requires a registered channel of the same type");
        }
        if (route.providers().containsKey(name) || (useAsDefault && route.defaultProvider() != null)) {
            throw new IllegalArgumentException("Provider name or default is already registered");
        }
        var providers = new HashMap<>(route.providers());
        providers.put(name, new NotificationClient.RegisteredProvider(
                notification -> provider.send(channel.type().cast(notification)), provider::validateConfiguration));
        routes.put(channel.id(), new NotificationClient.Route(channel, route.validator(),
                Map.copyOf(providers), useAsDefault ? name : route.defaultProvider()));
        return this;
    }

    /** Validates registered provider configuration and returns a client with an immutable registry snapshot. */
    public NotificationClient build() {
        try {
            for (var route : routes.values()) {
                for (var provider : route.providers().values()) {
                    provider.validateConfiguration().run();
                }
            }
        } catch (RuntimeException ignored) {
            throw new IllegalArgumentException("Invalid provider configuration");
        }
        return new NotificationClient(Map.copyOf(routes), executor, retryPolicy, retryWait, clock, List.copyOf(listeners));
    }
}
