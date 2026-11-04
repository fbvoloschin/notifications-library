package dev.notifications;

import dev.notifications.channels.EmailNotification;
import dev.notifications.channels.NotificationValidators;
import dev.notifications.channels.PushNotification;
import dev.notifications.channels.SmsNotification;
import dev.notifications.providers.FcmProvider;
import dev.notifications.providers.MailgunProvider;
import dev.notifications.providers.SendGridProvider;
import dev.notifications.providers.SimulatedRequest;
import dev.notifications.providers.SimulatedResponse;
import dev.notifications.providers.SimulatedTransport;
import dev.notifications.providers.TwilioProvider;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class ProviderAdaptersTest {
    private static final String DOMAIN = "mg.example.com";
    private static final String MAILGUN_KEY = "fake-mailgun-key";
    private static final String SID = "AC" + "0123456789abcdef" + "0123456789abcdef";
    private static final String TWILIO_TOKEN = "fake-twilio-token";
    private static final String FROM_NUMBER = "+12345678901";
    private static final String PROJECT = "test-project";
    private static final String ACCESS_TOKEN = "fake-access-token";
    private static final EmailNotification EMAIL = new EmailNotification("dev@example.com", "Subject", "Body");
    private static final SmsNotification SMS = new SmsNotification("+15551234567", "Hello");
    private static final PushNotification PUSH = new PushNotification("device", "Alert", "Check", Map.of("kind", "news"));

    @Test
    void emailProviderSwitchChangesOnlyConfigurationAndUsesEachProvidersSender() {
        var mailgunRequest = new AtomicReference<SimulatedRequest>();
        var sendGridRequest = new AtomicReference<SimulatedRequest>();
        var sendGrid = new SendGridProvider("fake-sendgrid", "grid@example.com",
                fixture(sendGridRequest, new SimulatedResponse(202)));
        var mailgun = new MailgunProvider(MAILGUN_KEY, DOMAIN, "mailgun@example.com",
                fixture(mailgunRequest, new SimulatedResponse(200, Map.of(), Map.of("id", "<email-9>", "message", "Queued"))));
        var request = new SendRequest(EMAIL);

        var acceptedByGrid = emailClient(sendGrid, mailgun, false).send(request);
        var acceptedByMailgun = emailClient(sendGrid, mailgun, true).send(request);

        assertEquals("sendgrid", acceptedByGrid.provider());
        assertEquals("mailgun", acceptedByMailgun.provider());
        assertEquals(Map.of("email", "grid@example.com"), sendGridRequest.get().payload().get("from"));
        assertContract(mailgunRequest.get(), "/v3/mg.example.com/messages", "multipart/form-data",
                "Basic YXBpOmZha2UtbWFpbGd1bi1rZXk=", Map.of(
                        "from", "mailgun@example.com", "to", EMAIL.recipient(),
                        "subject", EMAIL.subject(), "text", EMAIL.body()));
        assertEquals(Optional.of("<email-9>"), acceptedByMailgun.providerMessageId());
    }

    @Test
    void onlyTwilioCanBeConfiguredAndItsRequestUsesBasicAndFormFields() {
        var recorded = new AtomicReference<SimulatedRequest>();
        var adapter = new TwilioProvider(SID, TWILIO_TOKEN, FROM_NUMBER,
                fixture(recorded, new SimulatedResponse(201, Map.of(), Map.of("sid", "SM-message", "status", "queued"))));
        var client = NotificationClient.builder().registerChannel(SmsNotification.CHANNEL, NotificationValidators::validateSms)
                .registerProvider(SmsNotification.CHANNEL, "twilio", adapter, true).build();
        assertNull(recorded.get());

        var result = client.send(new SendRequest(SMS));

        assertEquals(Optional.of("SM-message"), result.providerMessageId());
        assertContract(recorded.get(), "/2010-04-01/Accounts/" + SID + "/Messages.json",
                "application/x-www-form-urlencoded",
                "Basic QUMwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjpmYWtlLXR3aWxpby10b2tlbg==",
                Map.of("To", SMS.recipient(), "From", FROM_NUMBER, "Body", SMS.body()));
    }

    @Test
    void fcmUsesOAuthAndOnlyIncludesDataWhenSupplied() {
        var recorded = new AtomicReference<SimulatedRequest>();
        var id = "projects/test-project/messages/1";
        var adapter = new FcmProvider(PROJECT, ACCESS_TOKEN,
                fixture(recorded, new SimulatedResponse(200, Map.of(), Map.of("name", id))));
        var client = NotificationClient.builder().registerChannel(PushNotification.CHANNEL, NotificationValidators::validatePush)
                .registerProvider(PushNotification.CHANNEL, "fcm", adapter, true).build();

        assertEquals(Optional.of(id), client.send(new SendRequest(PUSH)).providerMessageId());
        assertContract(recorded.get(), "/v1/projects/test-project/messages:send", "application/json",
                "Bearer " + ACCESS_TOKEN, Map.of("message", Map.of(
                        "token", PUSH.token(), "notification", Map.of("title", PUSH.title(), "body", PUSH.body()),
                        "data", Map.of("kind", "news"))));
        client.send(new SendRequest(new PushNotification("device", "Alert", "Check")));
        assertEquals(Map.of("message", Map.of("token", "device", "notification", Map.of("title", "Alert", "body", "Check"))),
                recorded.get().payload());
    }

    @Test
    void successStatusRequiresTheProviderSpecificFields() {
        assertUnexpected(() -> new MailgunProvider(MAILGUN_KEY, DOMAIN, "mailgun@example.com",
                request -> new SimulatedResponse(200, Map.of(), Map.of("message", "Queued"))).send(EMAIL));
        assertUnexpected(() -> new TwilioProvider(SID, TWILIO_TOKEN, FROM_NUMBER,
                request -> new SimulatedResponse(201, Map.of(), Map.of("sid", "SM-message", "status", 1))).send(SMS));
        assertUnexpected(() -> new FcmProvider(PROJECT, ACCESS_TOKEN,
                request -> new SimulatedResponse(200, Map.of(), Map.of("name", " "))).send(PUSH));
        assertUnexpected(() -> new FcmProvider(PROJECT, ACCESS_TOKEN, request -> new SimulatedResponse(204)).send(PUSH));
    }

    @Test
    void twilioRateLimitAndFcmUnavailableAreTransientAndPreserveDelay() {
        var twilio = new TwilioProvider(SID, TWILIO_TOKEN, FROM_NUMBER,
                request -> new SimulatedResponse(429, Map.of("Retry-After", "2"), Map.of()));
        var fcm = new FcmProvider(PROJECT, ACCESS_TOKEN,
                request -> new SimulatedResponse(503, Map.of("Retry-After", "17"), Map.of()));

        assertTransient(() -> twilio.send(SMS), Duration.ofSeconds(2));
        assertTransient(() -> fcm.send(PUSH), Duration.ofSeconds(17));
    }

    @Test
    void knownPreacceptanceFailureStaysTransientButAmbiguousTimeoutDoesNot() {
        var delay = Duration.ofSeconds(4);
        var offline = new FcmProvider(PROJECT, ACCESS_TOKEN,
                request -> { throw new ProviderFailure(ProviderFailure.Kind.TRANSIENT, delay); });
        var timeout = new FcmProvider(PROJECT, ACCESS_TOKEN,
                request -> { throw new ProviderFailure(ProviderFailure.Kind.UNKNOWN_ACCEPTANCE); });

        assertTransient(() -> offline.send(PUSH), delay);
        var uncertain = assertThrows(ProviderFailure.class, () -> timeout.send(PUSH));
        assertEquals(ProviderFailure.Kind.UNKNOWN_ACCEPTANCE, uncertain.kind());
        assertEquals(Duration.ZERO, uncertain.retryAfter());
    }

    @Test
    void registeredProvidersRejectMalformedConfigurationBeforeTransportRuns() {
        SimulatedTransport forbidden = request -> fail("No transport on build");
        assertBadConfig(EmailNotification.CHANNEL, NotificationValidators::validateEmail,
                new MailgunProvider(MAILGUN_KEY, "bad/path", "mailgun@example.com", forbidden));
        assertBadConfig(SmsNotification.CHANNEL, NotificationValidators::validateSms,
                new TwilioProvider(SID, TWILIO_TOKEN, "malformed sender", forbidden));
        assertBadConfig(PushNotification.CHANNEL, NotificationValidators::validatePush,
                new FcmProvider(PROJECT, " ", forbidden));
    }

    @Test
    void derivedAuthorizationEchoIsNotExposedAsMessageId() {
        var mailgun = new MailgunProvider(MAILGUN_KEY, DOMAIN, "mailgun@example.com",
                request -> new SimulatedResponse(200, Map.of(), Map.of(
                        "id", request.headers().get("Authorization").substring("Basic ".length()), "message", "Queued")));

        assertEquals(Optional.empty(), mailgun.send(EMAIL));
        assertFalse(mailgun.toString().contains(MAILGUN_KEY));
    }

    @Test
    void defaultFixturesReturnDeterministicProviderIdentifiers() {
        var mailgun = new MailgunProvider(MAILGUN_KEY, DOMAIN, "mailgun@example.com");
        var twilio = new TwilioProvider(SID, TWILIO_TOKEN, FROM_NUMBER);
        var fcm = new FcmProvider(PROJECT, ACCESS_TOKEN);

        for (int attempt = 0; attempt < 2; attempt++) {
            assertEquals(Optional.of("<simulated-mailgun@mg.example.com>"), mailgun.send(EMAIL));
            assertEquals(Optional.of("SM00000000000000000000000000000000"), twilio.send(SMS));
            assertEquals(Optional.of("projects/test-project/messages/simulated-fcm"), fcm.send(PUSH));
        }
    }

    private static NotificationClient emailClient(SendGridProvider sendGrid, MailgunProvider mailgun, boolean preferMailgun) {
        return NotificationClient.builder().registerChannel(EmailNotification.CHANNEL, NotificationValidators::validateEmail)
                .registerProvider(EmailNotification.CHANNEL, "sendgrid", sendGrid, !preferMailgun)
                .registerProvider(EmailNotification.CHANNEL, "mailgun", mailgun, preferMailgun).build();
    }

    private static SimulatedTransport fixture(AtomicReference<SimulatedRequest> recorded, SimulatedResponse response) {
        return request -> {
            recorded.set(request);
            return response;
        };
    }

    private static void assertContract(SimulatedRequest request, String path, String contentType,
                                       String authorization, Map<String, ?> payload) {
        assertEquals("POST", request.method());
        assertEquals(path, request.path());
        assertEquals(Map.of("Content-Type", contentType, "Authorization", authorization), request.headers());
        assertEquals(payload, request.payload());
    }

    private static void assertUnexpected(Runnable send) {
        assertEquals(ProviderFailure.Kind.UNEXPECTED_RESPONSE, assertThrows(ProviderFailure.class, send::run).kind());
    }

    private static void assertTransient(Runnable send, Duration delay) {
        var failure = assertThrows(ProviderFailure.class, send::run);
        assertEquals(ProviderFailure.Kind.TRANSIENT, failure.kind());
        assertEquals(delay, failure.retryAfter());
    }

    private static <N extends Notification> void assertBadConfig(
            Channel<N> channel, NotificationValidator<N> validator, NotificationProvider<N> provider) {
        var builder = NotificationClient.builder().registerChannel(channel, validator)
                .registerProvider(channel, "candidate", provider, false);
        var failure = assertThrows(IllegalArgumentException.class, builder::build);
        assertEquals("Invalid provider configuration", failure.getMessage());
        assertNull(failure.getCause());
    }
}
