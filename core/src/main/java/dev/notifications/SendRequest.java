package dev.notifications;

import java.util.Objects;
import java.util.Optional;

/** A notification and an optional case-sensitive provider name. */
public record SendRequest(Notification notification, Optional<String> provider) {
    public SendRequest {
        Objects.requireNonNull(notification, "Notification is required");
        Objects.requireNonNull(provider, "Provider selection is required");
    }

    public SendRequest(Notification notification) {
        this(notification, Optional.empty());
    }

    public SendRequest(Notification notification, String provider) {
        this(notification, Optional.of(provider));
    }
}
