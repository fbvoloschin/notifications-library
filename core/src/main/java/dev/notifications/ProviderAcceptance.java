package dev.notifications;

import java.util.Optional;
import java.util.UUID;

/** Provider acceptance, not confirmation of delivery to a recipient. */
public record ProviderAcceptance(String channel, String provider, int attempts,
                                 Optional<String> providerMessageId, UUID correlationId) { }
