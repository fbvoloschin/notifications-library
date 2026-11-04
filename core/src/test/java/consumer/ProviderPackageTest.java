package consumer;

import dev.notifications.EmailNotification;
import dev.notifications.NotificationClient;
import dev.notifications.NotificationProvider;
import dev.notifications.NotificationValidators;
import dev.notifications.SendRequest;
import dev.notifications.providers.SendGridProvider;
import dev.notifications.providers.SimulatedRequest;
import dev.notifications.providers.SimulatedResponse;
import dev.notifications.providers.SimulatedTransport;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderPackageTest {
    @Test
    void externalConsumerSendsViaAnAdapterAndFixtureInTheProvidersPackage() {
        var recorded = new AtomicReference<SimulatedRequest>();
        SimulatedTransport transport = request -> {
            recorded.set(request);
            return new SimulatedResponse(202, Map.of("X-Message-ID", "consumer-example-id"), Map.of());
        };
        NotificationProvider<EmailNotification> provider = new SendGridProvider(
                "demo-provider-key", "sender@example.com", transport);
        var client = NotificationClient.builder()
                .registerChannel(EmailNotification.CHANNEL, NotificationValidators::validateEmail)
                .registerProvider(EmailNotification.CHANNEL, "sendgrid", provider, true).build();

        var result = client.send(new SendRequest(new EmailNotification(
                "recipient@example.com", "Package verification", "Delivered to the offline adapter")));

        assertEquals("/v3/mail/send", recorded.get().path());
        assertEquals("Package verification", recorded.get().payload().get("subject"));
        assertEquals("sendgrid", result.provider());
        assertEquals(Optional.of("consumer-example-id"), result.providerMessageId());
    }

    @Test
    void sharedEmailSyntaxCheckIsAvailableToAnExternalAdapter() {
        assertTrue(NotificationValidators.validEmailAddress("name+tag@team.example.com"));
        assertFalse(NotificationValidators.validEmailAddress(null));
        assertFalse(NotificationValidators.validEmailAddress("name@example"));
        assertFalse(NotificationValidators.validEmailAddress("name..part@example.com"));
    }

    @Test
    void sharedPhoneSyntaxCheckIsAvailableToAnExternalAdapter() {
        assertTrue(NotificationValidators.validPhoneNumber("+12345678"));
        assertTrue(NotificationValidators.validPhoneNumber("+123456789012345"));
        assertFalse(NotificationValidators.validPhoneNumber(null));
        assertFalse(NotificationValidators.validPhoneNumber("+02345678"));
        assertFalse(NotificationValidators.validPhoneNumber("+1234567890123456"));
    }
}
