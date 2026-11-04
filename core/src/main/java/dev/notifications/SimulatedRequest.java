package dev.notifications;

import java.util.Map;

/** In-memory request. Headers and payload are intended for the simulation, not logging. */
public record SimulatedRequest(String method, String path, Map<String, String> headers, Map<String, ?> payload) {
    public SimulatedRequest {
        headers = Map.copyOf(headers);
        payload = Map.copyOf(payload);
    }

    @Override
    public String toString() {
        return "SimulatedRequest[details redacted]";
    }
}
