package dev.notifications;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Registers typed channels and named providers in Java. */
public final class NotificationClientBuilder {
    private final Map<String, NotificationClient.Route> routes = new HashMap<>();
    private Executor executor;
    private RetryPolicy retryPolicy = RetryPolicy.defaults();
    private RetryWait retryWait = Thread::sleep;

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
        return new NotificationClient(Map.copyOf(routes), executor, retryPolicy, retryWait);
    }
}
