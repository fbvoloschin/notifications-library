package dev.notifications;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static dev.notifications.SendException.Reason.*;

/** Sends notifications through an immutable registry of configured providers. */
public final class NotificationClient {
    record RegisteredProvider(NotificationProvider<Notification> sender, Runnable validateConfiguration) { }

    record Route(Channel<?> channel, NotificationValidator<Notification> validator,
                 Map<String, RegisteredProvider> providers, String defaultProvider) { }

    private final Map<String, Route> routes;

    NotificationClient(Map<String, Route> routes) {
        this.routes = routes;
    }

    public static NotificationClientBuilder builder() {
        return new NotificationClientBuilder();
    }

    public ProviderAcceptance send(SendRequest request) {
        var correlationId = UUID.randomUUID();
        var notification = request.notification();
        var channel = notification.channel();
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

        try {
            var messageId = Objects.requireNonNull(provider.sender().send(notification));
            return new ProviderAcceptance(channel.id(), providerName, 1, messageId, correlationId);
        } catch (ProviderFailure classified) {
            throw new SendException(classified.kind().reason, correlationId, channel.id(), providerName, 1, List.of());
        } catch (RuntimeException ignored) {
            throw new SendException(UNEXPECTED_PROVIDER_FAILURE, correlationId, channel.id(),
                    providerName, 1, List.of());
        }
    }

    private static SendException unresolved(SendException.Reason reason, UUID id, String channel) {
        return new SendException(reason, id, channel, null, 0, List.of());
    }
}
