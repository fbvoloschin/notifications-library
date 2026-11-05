package dev.notifications;

import dev.notifications.channels.EmailNotification;
import dev.notifications.channels.NotificationValidators;
import dev.notifications.providers.SendGridProvider;
import dev.notifications.providers.SimulatedRequest;
import dev.notifications.providers.SimulatedResponse;
import dev.notifications.providers.SimulatedTransport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class SendGridProviderTest {
    private static final String KEY = "fake-test-key";
    private static final String SENDER = "sender@example.com";
    private static final EmailNotification EMAIL = new EmailNotification("recipient@example.com", "Subject", "Content");

    @Test
    void sendsTheMailSendContractAndReadsTheOptionalResponseHeader() {
        var recorded = new AtomicReference<SimulatedRequest>();
        var client = client(new SendGridProvider(KEY, SENDER, request -> {
            recorded.set(request);
            return new SimulatedResponse(202, Map.of("x-message-id", "message-7"), Map.of());
        }));

        var accepted = client.send(new SendRequest(EMAIL));

        var request = recorded.get();
        assertEquals("POST", request.method());
        assertEquals("/v3/mail/send", request.path());
        assertEquals(Map.of("Authorization", "Bearer " + KEY, "Content-Type", "application/json"), request.headers());
        assertEquals(Map.of(
                "personalizations", List.of(Map.of("to", List.of(Map.of("email", EMAIL.recipient())))),
                "from", Map.of("email", SENDER),
                "subject", EMAIL.subject(),
                "content", List.of(Map.of("type", "text/plain", "value", EMAIL.body()))), request.payload());
        assertEquals(Optional.of("message-7"), accepted.providerMessageId());
        assertFalse(request.toString().contains(KEY));
        assertFalse(request.toString().contains(EMAIL.body()));
    }

    @Test
    void aNormalAcceptedFixtureDoesNotInventAnId() {
        var acceptance = client(new SendGridProvider(KEY, SENDER)).send(new SendRequest(EMAIL));

        assertEquals(Optional.empty(), acceptance.providerMessageId());
        assertEquals("sendgrid", acceptance.provider());
    }

    @Test
    void unexpectedSuccessDoesNotBecomeAcceptance() {
        for (var response : List.of(new SimulatedResponse(204), new SimulatedResponse(202, Map.of(), Map.of("extra", 1)))) {
            var failure = assertThrows(SendException.class,
                    () -> client(new SendGridProvider(KEY, SENDER, request -> response)).send(new SendRequest(EMAIL)));

            assertEquals(SendException.Reason.UNEXPECTED_PROVIDER_RESPONSE, failure.reason());
            assertEquals(1, failure.attempts());
        }
    }

    @Test
    void aDocumentedRateLimitPreservesANormalizedRetryDelay() {
        var adapter = new SendGridProvider(KEY, SENDER,
                request -> new SimulatedResponse(429, Map.of("retry-after", " 10 "), Map.of()));

        var failure = assertThrows(ProviderFailure.class, () -> adapter.send(EMAIL));

        assertEquals(ProviderFailure.Kind.TRANSIENT, failure.kind());
        assertEquals(Duration.ofSeconds(10), failure.retryAfter());
        assertEquals(SendException.Reason.PROVIDER_TRANSIENT,
                assertThrows(SendException.class, () -> client(adapter).send(new SendRequest(EMAIL))).reason());
    }

    @Test
    void absentOrMalformedRetryDelayDoesNotChangeTheClassification() {
        for (var retryAfter : List.of("", "-1", "9223372036854775808")) {
            var adapter = new SendGridProvider(KEY, SENDER,
                    request -> new SimulatedResponse(429, Map.of("Retry-After", retryAfter), Map.of()));

            var failure = assertThrows(ProviderFailure.class, () -> adapter.send(EMAIL));

            assertEquals(ProviderFailure.Kind.TRANSIENT, failure.kind());
            assertEquals(Duration.ZERO, failure.retryAfter());
        }
    }

    @Test
    void ambiguousServerFailureAndSimulatedTimeoutRemainNonRetryable() {
        SimulatedTransport serverError = request -> new SimulatedResponse(500);
        SimulatedTransport timeout = request -> { throw new ProviderFailure(ProviderFailure.Kind.UNKNOWN_ACCEPTANCE); };
        for (var transport : List.of(serverError, timeout)) {
            var client = client(new SendGridProvider(KEY, SENDER, transport));

            var failure = assertThrows(SendException.class, () -> client.send(new SendRequest(EMAIL)));

            assertEquals(SendException.Reason.PROVIDER_ACCEPTANCE_UNKNOWN, failure.reason());
            assertEquals(1, failure.attempts());
        }
    }

    @Test
    void aMessageIdThatEchoesACredentialIsNotExposed() {
        var provider = new SendGridProvider(KEY, SENDER,
                request -> new SimulatedResponse(202, Map.of("X-Message-ID", "Bearer " + KEY), Map.of()));

        assertEquals(Optional.empty(), client(provider).send(new SendRequest(EMAIL)).providerMessageId());
    }

    @Test
    void localConfigurationFailsDuringBuildWithoutTouchingTransport() {
        SimulatedTransport forbidden = request -> fail("Transport must not run during build");
        for (var adapter : List.of(new SendGridProvider(null, SENDER, forbidden),
                new SendGridProvider(KEY, "invalid " + KEY, forbidden))) {
            var failure = assertThrows(IllegalArgumentException.class, () -> client(adapter));

            assertEquals("Invalid provider configuration", failure.getMessage());
            assertNull(failure.getCause());
            assertFalse(adapter.toString().contains(KEY));
        }
    }

    @Test
    void providerResponsesAndTransportFailuresDoNotLeakCredentials() {
        var denied = new SimulatedResponse(401, Map.of("Echo", "Bearer " + KEY), Map.of("error", KEY));
        var rejection = assertThrows(SendException.class,
                () -> client(new SendGridProvider(KEY, SENDER, request -> denied)).send(new SendRequest(EMAIL)));
        assertEquals(SendException.Reason.PROVIDER_REJECTED, rejection.reason());
        assertFalse(denied.toString().contains(KEY));
        assertFalse(rejection.toString().contains(KEY));
        assertNull(rejection.getCause());

        var adapter = new SendGridProvider(KEY, SENDER, request -> {
            throw new IllegalStateException("Bearer " + KEY);
        });
        var failure = assertThrows(ProviderFailure.class, () -> adapter.send(EMAIL));
        assertEquals(ProviderFailure.Kind.UNEXPECTED_FAILURE, failure.kind());
        assertFalse(failure.toString().contains(KEY));
        assertNull(failure.getCause());
    }

    private static NotificationClient client(SendGridProvider provider) {
        return NotificationClient.builder()
                .registerChannel(EmailNotification.CHANNEL, NotificationValidators::validateEmail)
                .registerProvider(EmailNotification.CHANNEL, "sendgrid", provider, true)
                .build();
    }
}
