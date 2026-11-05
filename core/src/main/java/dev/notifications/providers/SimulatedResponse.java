package dev.notifications.providers;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Structured offline response fixture; malformed field values may be used to exercise adapters. */
public record SimulatedResponse(int status, Map<String, String> headers, Map<String, ?> body) {
    public SimulatedResponse {
        headers = Map.copyOf(headers);
        body = Collections.unmodifiableMap(new LinkedHashMap<>(body));
    }

    public SimulatedResponse(int status) {
        this(status, Map.of(), Map.of());
    }

    Optional<String> header(String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue).findFirst();
    }

    @Override
    public String toString() {
        return "SimulatedResponse[status=" + status + ", details redacted]";
    }
}
