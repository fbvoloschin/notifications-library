package dev.notifications;

import java.util.List;

/** Returns safe, field-level violations or an empty list when valid. */
@FunctionalInterface
public interface NotificationValidator<N extends Notification> {
    List<String> validate(N notification);
}
