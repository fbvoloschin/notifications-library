package dev.notifications.channels;

import dev.notifications.Channel;
import dev.notifications.Notification;

/** SMS content; the selected provider supplies the configured sender. */
public record SmsNotification(String recipient, String body) implements Notification {
    public static final Channel<SmsNotification> CHANNEL = new Channel<>("sms", SmsNotification.class);

    @Override
    public Channel<SmsNotification> channel() {
        return CHANNEL;
    }
}
