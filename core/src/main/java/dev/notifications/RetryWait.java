package dev.notifications;

import java.time.Duration;

/** Interruptible wait on the send thread, replaceable by a deterministic test double. */
@FunctionalInterface
public interface RetryWait {
    void await(Duration delay) throws InterruptedException;
}
