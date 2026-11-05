package dev.notifications.providers;

import dev.notifications.ProviderFailure;

/** Supplies offline fixtures; implementations must not perform network I/O. */
@FunctionalInterface
public interface SimulatedTransport {
    /** May throw a classified {@link ProviderFailure} for simulated transport faults. */
    SimulatedResponse exchange(SimulatedRequest request);
}
