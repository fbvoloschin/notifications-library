package dev.notifications;

import java.util.Optional;

/** Accepts a typed notification and returns the upstream ID when one is supplied. */
@FunctionalInterface
public interface NotificationProvider<N extends Notification> {
    Optional<String> send(N notification);
}
