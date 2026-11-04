package dev.notifications;

import java.util.List;

/** Returns safe, field-level violations or an empty list when valid. */
@FunctionalInterface
public interface NotificationValidator<N extends Notification> {
    /** Returns safe field-level violations, or an empty list when the notification is valid. */
    List<String> validate(N notification);
}
