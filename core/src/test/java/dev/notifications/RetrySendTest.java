package dev.notifications;

import dev.notifications.providers.FcmProvider;
import dev.notifications.providers.SimulatedResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class RetrySendTest {
    private static final Duration SECOND = Duration.ofSeconds(1);
    private static final Duration CAP = Duration.ofSeconds(4);
    private static final EmailNotification EMAIL = new EmailNotification("dev@example.com", "Subject", "Body");
    private static final SendRequest REQUEST = new SendRequest(EMAIL);

    @Test
    void retriesAreDisabledByDefault() {
        var invocations = new AtomicInteger();
        var client = builder(email -> {
            invocations.incrementAndGet();
            throw new ProviderFailure(ProviderFailure.Kind.TRANSIENT);
        }).retryWait(delay -> fail("A default policy must not wait")).build();

        var failure = assertThrows(SendException.class, () -> client.send(REQUEST));

        assertEquals(SendException.Reason.PROVIDER_TRANSIENT, failure.reason());
        assertEquals(1, failure.attempts());
        assertEquals(1, invocations.get());
        assertEquals(new RetryPolicy(1, SECOND, Duration.ofSeconds(30)), RetryPolicy.defaults());
    }

    @Test
    void transientRecoveryUsesTheSameProviderAndWaitsForTheLargerProviderMinimum() {
        var invocations = new AtomicInteger();
        var waits = new ArrayList<Duration>();
        var minimum = Duration.ofSeconds(10);
        var client = builder(email -> {
            assertSame(EMAIL, email);
            if (invocations.incrementAndGet() == 1) {
                throw new ProviderFailure(ProviderFailure.Kind.TRANSIENT, minimum);
            }
            return Optional.of("accepted");
        }).retryPolicy(new RetryPolicy(2, SECOND, CAP)).retryWait(waits::add).build();

        var acceptance = client.send(REQUEST);

        assertEquals("primary", acceptance.provider());
        assertEquals(2, acceptance.attempts());
        assertEquals(2, invocations.get());
        assertEquals(Optional.of("accepted"), acceptance.providerMessageId());
        assertEquals(List.of(minimum), waits);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exponentialBackoffSaturatesAndStopsAtTheConfiguredAttemptLimit(boolean recovers) {
        var invocations = new AtomicInteger();
        var waits = new ArrayList<Duration>();
        var client = builder(email -> {
            if (invocations.incrementAndGet() == 5 && recovers) {
                return Optional.of("accepted");
            }
            throw new ProviderFailure(ProviderFailure.Kind.TRANSIENT);
        }).retryPolicy(new RetryPolicy(5, SECOND, CAP)).retryWait(waits::add).build();

        if (recovers) {
            assertEquals(5, client.send(REQUEST).attempts());
        } else {
            var failure = assertThrows(SendException.class, () -> client.send(REQUEST));
            assertEquals(SendException.Reason.PROVIDER_TRANSIENT, failure.reason());
            assertEquals(5, failure.attempts());
        }
        assertEquals(5, invocations.get());
        assertEquals(List.of(SECOND, Duration.ofSeconds(2), CAP, CAP), waits);
    }

    @Test
    void extremeDurationBackoffSaturatesWithoutOverflow() {
        var initial = Duration.ofSeconds(Long.MAX_VALUE / 2 + 1, 500);
        var cap = Duration.ofSeconds(Long.MAX_VALUE, 999_999_999);
        var invocations = new AtomicInteger();
        var waits = new ArrayList<Duration>();
        var client = builder(email -> {
            if (invocations.incrementAndGet() <= 2) {
                throw new ProviderFailure(ProviderFailure.Kind.TRANSIENT);
            }
            return Optional.empty();
        }).retryPolicy(new RetryPolicy(3, initial, cap)).retryWait(waits::add).build();

        assertEquals(3, client.send(REQUEST).attempts());
        assertEquals(List.of(initial, cap), waits);
    }

    @Test
    void nonTransientProviderFailuresDoNotWaitOrFallBack() {
        var expected = Map.of(
                ProviderFailure.Kind.REJECTED, SendException.Reason.PROVIDER_REJECTED,
                ProviderFailure.Kind.UNKNOWN_ACCEPTANCE, SendException.Reason.PROVIDER_ACCEPTANCE_UNKNOWN,
                ProviderFailure.Kind.UNEXPECTED_RESPONSE, SendException.Reason.UNEXPECTED_PROVIDER_RESPONSE,
                ProviderFailure.Kind.UNEXPECTED_FAILURE, SendException.Reason.UNEXPECTED_PROVIDER_FAILURE);
        for (var entry : expected.entrySet()) {
            var invocations = new AtomicInteger();
            var client = builder(email -> {
                invocations.incrementAndGet();
                throw new ProviderFailure(entry.getKey());
            }).retryPolicy(new RetryPolicy(5, SECOND, CAP)).retryWait(delay -> fail("No retry")).build();

            var failure = assertThrows(SendException.class, () -> client.send(REQUEST));

            assertEquals(entry.getValue(), failure.reason());
            assertEquals(1, failure.attempts());
            assertEquals(1, invocations.get());
        }
    }

    @Test
    void interruptionRestoresTheFlagAndDoesNotInvokeTheProviderAgain() {
        var invocations = new AtomicInteger();
        var client = builder(email -> {
            invocations.incrementAndGet();
            throw new ProviderFailure(ProviderFailure.Kind.TRANSIENT);
        }).retryPolicy(new RetryPolicy(3, SECOND, CAP)).retryWait(delay -> {
            throw new InterruptedException("private waiter details");
        }).build();

        try {
            var failure = assertThrows(SendException.class, () -> client.send(REQUEST));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(SendException.Reason.INTERRUPTED, failure.reason());
            assertEquals(1, failure.attempts());
            assertEquals(Optional.of("primary"), failure.provider());
            assertEquals(1, invocations.get());
            assertNull(failure.getCause());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void invalidRetryConfigurationFailsBeforeAnySend() {
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(0, SECOND, CAP));
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(2, Duration.ZERO, CAP));
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(2, SECOND.negated(), CAP));
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(2, CAP, SECOND));
    }

    @Test
    void aBrokenCustomWaiterFailsSafelyWithoutAnotherProviderCall() {
        var invocations = new AtomicInteger();
        var client = builder(email -> {
            invocations.incrementAndGet();
            throw new ProviderFailure(ProviderFailure.Kind.TRANSIENT);
        }).retryPolicy(new RetryPolicy(3, SECOND, CAP)).retryWait(delay -> {
            throw new IllegalStateException("private waiter details");
        }).build();

        var failure = assertThrows(SendException.class, () -> client.send(REQUEST));

        assertEquals(SendException.Reason.UNEXPECTED_RETRY_WAIT, failure.reason());
        assertEquals(1, failure.attempts());
        assertEquals(1, invocations.get());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("private waiter details"));
    }

    @Test
    void asyncFcmSendUsesTheSameRetryPolicyAndConsumesTheNormalizedDelay() throws Exception {
        var calls = new AtomicInteger();
        var waits = new ArrayList<Duration>();
        var adapter = new FcmProvider("example-project", "fake-access", request -> {
            if (calls.incrementAndGet() == 1) {
                return new SimulatedResponse(503, Map.of("Retry-After", "8"), Map.of());
            }
            return new SimulatedResponse(200, Map.of(), Map.of("name", "projects/example-project/messages/ok"));
        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var client = NotificationClient.builder().executor(executor)
                    .registerChannel(PushNotification.CHANNEL, NotificationValidators::validatePush)
                    .registerProvider(PushNotification.CHANNEL, "fcm", adapter, true)
                    .retryPolicy(new RetryPolicy(2, SECOND, CAP)).retryWait(waits::add).build();

            var result = client.sendAsync(new SendRequest(new PushNotification("token", "Title", "Body")))
                    .get(5, TimeUnit.SECONDS);

            assertEquals(2, result.attempts());
            assertEquals(2, calls.get());
            assertEquals(List.of(Duration.ofSeconds(8)), waits);
        }
    }

    private static NotificationClientBuilder builder(NotificationProvider<EmailNotification> provider) {
        return NotificationClient.builder().registerChannel(EmailNotification.CHANNEL, NotificationValidators::validateEmail)
                .registerProvider(EmailNotification.CHANNEL, "primary", provider, true)
                .registerProvider(EmailNotification.CHANNEL, "fallback", email -> fail("Provider fallback is forbidden"), false);
    }
}
