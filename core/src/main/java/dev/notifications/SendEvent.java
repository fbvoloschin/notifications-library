package dev.notifications;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Safe send status. The channel can be unavailable; only failed events have a reason.
 * Acceptance is the provider's acknowledgment, not final delivery.
 */
public record SendEvent(Type type, UUID correlationId, Instant timestamp, String channel,
                        Optional<String> provider, int attempts, Optional<SendException.Reason> reason) {
    public enum Type { RETRYING, ACCEPTED, FAILED }
}
