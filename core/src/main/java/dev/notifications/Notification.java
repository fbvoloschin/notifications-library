package dev.notifications;

/** Content to send through an open, typed channel. */
public interface Notification {
    /** Returns the typed channel used to route this notification. */
    Channel<? extends Notification> channel();
}
