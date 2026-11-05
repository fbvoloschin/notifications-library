package dev.notifications.channels;

import dev.notifications.Channel;
import dev.notifications.Notification;

/** Email content; the selected provider supplies the configured sender. */
public record EmailNotification(String recipient, String subject, String body) implements Notification {
    public static final Channel<EmailNotification> CHANNEL = new Channel<>("email", EmailNotification.class);

    @Override
    public Channel<EmailNotification> channel() {
        return CHANNEL;
    }
}
