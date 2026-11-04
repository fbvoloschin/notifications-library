package dev.notifications;

import java.time.Duration;

/** Bounded exponential retry policy. Max attempts includes the first provider call. */
public record RetryPolicy(int maxAttempts, Duration initialDelay, Duration maxBackoff) {
    private static final RetryPolicy DEFAULT = new RetryPolicy(1, Duration.ofSeconds(1), Duration.ofSeconds(30));

    public RetryPolicy {
        if (maxAttempts < 1 || initialDelay == null || initialDelay.isZero() || initialDelay.isNegative()
                || maxBackoff == null || maxBackoff.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("Retry policy requires positive attempts and delay, with cap at least the delay");
        }
    }

    /** Returns the default policy: one attempt, a one-second delay and a thirty-second cap. */
    public static RetryPolicy defaults() {
        return DEFAULT;
    }

    Duration nextBackoff(Duration current) {
        // Compare before multiplying, even when twice the current delay is not representable.
        return current.compareTo(maxBackoff.minus(current)) >= 0 ? maxBackoff : current.multipliedBy(2);
    }
}
