package dev.notifications;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Registers typed channels and named providers in Java. */
public final class NotificationClientBuilder {
    private final Map<String, NotificationClient.Route> routes = new HashMap<>();

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
        providers.put(name, notification -> provider.send(channel.type().cast(notification)));
        routes.put(channel.id(), new NotificationClient.Route(channel, route.validator(),
                Map.copyOf(providers), useAsDefault ? name : route.defaultProvider()));
        return this;
    }

    public NotificationClient build() {
        return new NotificationClient(Map.copyOf(routes));
    }
}
