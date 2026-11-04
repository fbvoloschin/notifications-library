package dev.notifications;

import java.util.Objects;

/** An extensible channel identifier paired with its notification type. */
public record Channel<N extends Notification>(String id, Class<N> type) {
    public Channel {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Channel identifier is required");
        }
        Objects.requireNonNull(type, "Notification type is required");
    }
}
