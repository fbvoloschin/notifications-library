package dev.notifications;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Push content with optional custom data, snapshotted at construction. */
public record PushNotification(String token, String title, String body, Map<String, String> data)
        implements Notification {
    public static final Channel<PushNotification> CHANNEL = new Channel<>("push", PushNotification.class);

    public PushNotification {
        // Retain malformed entries for the validator to report with the other fields.
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public PushNotification(String token, String title, String body) {
        this(token, title, body, Map.of());
    }

    @Override
    public Channel<PushNotification> channel() {
        return CHANNEL;
    }
}
