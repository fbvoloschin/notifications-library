package dev.notifications;

/** Content to send through an open, typed channel. */
public interface Notification {
    Channel<? extends Notification> channel();
}
