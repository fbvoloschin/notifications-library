package dev.notifications;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** A classified send failure without upstream diagnostic content. */
public final class SendException extends RuntimeException {
    public enum Reason {
        UNKNOWN_CHANNEL, CHANNEL_TYPE_MISMATCH, INVALID_NOTIFICATION, UNEXPECTED_VALIDATION,
        MISSING_DEFAULT, UNKNOWN_PROVIDER, PROVIDER_TRANSIENT, PROVIDER_REJECTED,
        PROVIDER_ACCEPTANCE_UNKNOWN, UNEXPECTED_PROVIDER_RESPONSE, UNEXPECTED_PROVIDER_FAILURE,
        EXECUTION_REJECTED
    }

    private final Reason reason;
    private final UUID correlationId;
    private final String channel;
    private final String provider;
    private final int attempts;
    private final List<String> violations;

    SendException(Reason reason, UUID correlationId, String channel, String provider,
                  int attempts, List<String> violations) {
        super("Notification send failed: " + reason);
        this.reason = reason;
        this.correlationId = correlationId;
        this.channel = channel;
        this.provider = provider;
        this.attempts = attempts;
        this.violations = List.copyOf(violations);
    }

    public Reason reason() { return reason; }
    public UUID correlationId() { return correlationId; }
    /** The known channel identifier, or {@code null} if unavailable. */
    public String channel() { return channel; }
    public Optional<String> provider() { return Optional.ofNullable(provider); }
    public int attempts() { return attempts; }
    public List<String> violations() { return violations; }
}
