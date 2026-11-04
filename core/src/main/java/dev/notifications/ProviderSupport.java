package dev.notifications;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class ProviderSupport {
    private ProviderSupport() { }

    static String basicToken(String username, String credential) {
        return Base64.getEncoder().encodeToString((username + ":" + credential).getBytes(StandardCharsets.UTF_8));
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static String requireField(SimulatedResponse response, String field) {
        if (response.body().get(field) instanceof String value && hasText(value)) {
            return value;
        }
        throw new ProviderFailure(ProviderFailure.Kind.UNEXPECTED_RESPONSE);
    }

    static SimulatedResponse exchange(SimulatedTransport transport, SimulatedRequest request) {
        try {
            return Objects.requireNonNull(transport.exchange(request));
        } catch (ProviderFailure classified) {
            throw classified;
        } catch (RuntimeException ignored) {
            throw new ProviderFailure(ProviderFailure.Kind.UNEXPECTED_FAILURE);
        }
    }

    static ProviderFailure failure(SimulatedResponse response, Set<Integer> transientStatuses) {
        int status = response.status();
        if (transientStatuses.contains(status)) {
            return new ProviderFailure(ProviderFailure.Kind.TRANSIENT, retryAfter(response));
        }
        if (status >= 400 && status < 500) {
            return new ProviderFailure(ProviderFailure.Kind.REJECTED);
        }
        if (status >= 500 && status < 600) {
            return new ProviderFailure(ProviderFailure.Kind.UNKNOWN_ACCEPTANCE);
        }
        return new ProviderFailure(ProviderFailure.Kind.UNEXPECTED_RESPONSE);
    }

    static Optional<String> safeId(String id, String... credentials) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        for (String credential : credentials) {
            if (credential != null && !credential.isBlank() && id.contains(credential)) {
                return Optional.empty();
            }
        }
        return Optional.of(id);
    }

    // Offline fixtures support HTTP delta-seconds; missing, malformed or overflowing values have no minimum.
    private static Duration retryAfter(SimulatedResponse response) {
        var seconds = response.header("Retry-After").orElse("").strip();
        if (seconds.isEmpty() || !seconds.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return Duration.ZERO;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(seconds));
        } catch (NumberFormatException ignored) {
            return Duration.ZERO;
        }
    }
}
