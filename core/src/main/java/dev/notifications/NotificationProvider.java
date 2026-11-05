package dev.notifications;

import java.util.Optional;

/** Accepts a typed notification and returns the upstream ID when one is supplied. */
@FunctionalInterface
public interface NotificationProvider<N extends Notification> {
    /** Submits a notification and returns an upstream message identifier when one is available. */
    Optional<String> send(N notification);

    /** Checks only this provider's local configuration before the client is built. */
    default void validateConfiguration() { }
}
