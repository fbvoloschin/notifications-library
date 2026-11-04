package dev.notifications;

import java.time.Duration;
import java.util.Objects;

/** Safe, classified provider failure. A zero retry delay means no minimum wait was supplied. */
public final class ProviderFailure extends RuntimeException {
    public enum Kind {
        TRANSIENT(SendException.Reason.PROVIDER_TRANSIENT),
        REJECTED(SendException.Reason.PROVIDER_REJECTED),
        UNKNOWN_ACCEPTANCE(SendException.Reason.PROVIDER_ACCEPTANCE_UNKNOWN),
        UNEXPECTED_RESPONSE(SendException.Reason.UNEXPECTED_PROVIDER_RESPONSE),
        UNEXPECTED_FAILURE(SendException.Reason.UNEXPECTED_PROVIDER_FAILURE);

        final SendException.Reason reason;

        Kind(SendException.Reason reason) {
            this.reason = reason;
        }
    }

    private final Kind kind;
    private final Duration retryAfter;

    public ProviderFailure(Kind kind) {
        this(kind, Duration.ZERO);
    }

    public ProviderFailure(Kind kind, Duration retryAfter) {
        super("Provider failed: " + Objects.requireNonNull(kind), null, false, true);
        if (Objects.requireNonNull(retryAfter).isNegative() || (kind != Kind.TRANSIENT && !retryAfter.isZero())) {
            throw new IllegalArgumentException("Only a transient failure can supply a nonnegative retry delay");
        }
        this.kind = kind;
        this.retryAfter = retryAfter;
    }

    public Kind kind() { return kind; }
    public Duration retryAfter() { return retryAfter; }
}
