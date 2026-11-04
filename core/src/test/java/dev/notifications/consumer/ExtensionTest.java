package dev.notifications.consumer;

import dev.notifications.Channel;
import dev.notifications.EmailNotification;
import dev.notifications.Notification;
import dev.notifications.NotificationClient;
import dev.notifications.NotificationValidators;
import dev.notifications.PushNotification;
import dev.notifications.SendException;
import dev.notifications.SendRequest;
import dev.notifications.SmsNotification;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class ExtensionTest {
    private record InboxNotification(String text) implements Notification {
        static final Channel<InboxNotification> CHANNEL = new Channel<>("inbox", InboxNotification.class);

        @Override
        public Channel<InboxNotification> channel() {
            return CHANNEL;
        }
    }

    @Test
    void consumerCanAddAChannelAndAnAlternativeProviderWithoutChangingSend() {
        var client = NotificationClient.builder()
                .registerChannel(EmailNotification.CHANNEL, NotificationValidators::validateEmail)
                .registerProvider(EmailNotification.CHANNEL, "original", email -> Optional.of("default"), true)
                .registerProvider(EmailNotification.CHANNEL, "local", email -> Optional.of(email.subject()), false)
                .registerChannel(InboxNotification.CHANNEL, inbox -> inbox.text().isBlank() ? List.of("text") : List.of())
                .registerProvider(InboxNotification.CHANNEL, "local", inbox -> Optional.of(inbox.text()), true)
                .build();
        var email = new EmailNotification("dev@example.org", "Example", "Content");

        assertEquals(Optional.of("Example"), client.send(new SendRequest(email, "local")).providerMessageId());
        assertEquals(Optional.of("hello"), client.send(new SendRequest(new InboxNotification("hello"))).providerMessageId());
        assertEquals("original", client.send(new SendRequest(email)).provider());
    }

    @Test
    void smsAndPushUseTheSameSendContractAsEmail() {
        var client = NotificationClient.builder()
                .registerChannel(SmsNotification.CHANNEL, NotificationValidators::validateSms)
                .registerProvider(SmsNotification.CHANNEL, "sms-double", sms -> Optional.of(sms.body()), true)
                .registerChannel(PushNotification.CHANNEL, NotificationValidators::validatePush)
                .registerProvider(PushNotification.CHANNEL, "push-double", push -> Optional.of(push.title()), true)
                .build();
        var sms = client.send(new SendRequest(new SmsNotification("+12345678", "sms")));
        var push = client.send(new SendRequest(new PushNotification("token", "push", "message")));

        assertEquals("sms", sms.channel());
        assertEquals(Optional.of("sms"), sms.providerMessageId());
        assertEquals("push", push.channel());
        assertEquals(Optional.of("push"), push.providerMessageId());
        assertNotEquals(sms.correlationId(), push.correlationId());
    }

    @Test
    void unknownRoutesAndMismatchedTypesCannotReachAProvider() {
        var client = NotificationClient.builder()
                .registerChannel(EmailNotification.CHANNEL, NotificationValidators::validateEmail)
                .registerProvider(EmailNotification.CHANNEL, "default",
                        email -> fail("Unexpected provider invocation"), true)
                .build();

        assertRouteFailure(client, new SendRequest(new InboxNotification("hello")), SendException.Reason.UNKNOWN_CHANNEL);
        assertRouteFailure(client, new SendRequest(() -> EmailNotification.CHANNEL), SendException.Reason.CHANNEL_TYPE_MISMATCH);
        var email = new EmailNotification("dev@example.org", "Subject", "Body");
        assertRouteFailure(client, new SendRequest(email, "Default"), SendException.Reason.UNKNOWN_PROVIDER);
    }

    @Test
    void aSelectedProviderFailureDoesNotFallBackOrExposeItsCause() {
        var client = NotificationClient.builder()
                .registerChannel(InboxNotification.CHANNEL, inbox -> List.of())
                .registerProvider(InboxNotification.CHANNEL, "default", inbox -> fail("No fallback"), true)
                .registerProvider(InboxNotification.CHANNEL, "selected", inbox -> {
                    throw new IllegalStateException("private-upstream-value");
                }, false)
                .build();

        var failure = assertThrows(SendException.class,
                () -> client.send(new SendRequest(new InboxNotification("test"), "selected")));

        assertEquals(SendException.Reason.UNEXPECTED_PROVIDER_FAILURE, failure.reason());
        assertEquals(Optional.of("selected"), failure.provider());
        assertEquals(1, failure.attempts());
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("private-upstream-value"));
    }

    @Test
    void registrationFailureAndLaterBuilderChangesDoNotChangeABuiltClient() {
        var channel = InboxNotification.CHANNEL;
        var builder = NotificationClient.builder().registerChannel(channel, notification -> List.of())
                .registerProvider(channel, "primary", inbox -> Optional.of("original"), true);
        var client = builder.build();
        var inbox = new InboxNotification("message");

        assertThrows(IllegalArgumentException.class,
                () -> builder.registerChannel(channel, notification -> List.of("text")));
        assertThrows(IllegalArgumentException.class,
                () -> builder.registerProvider(channel, "primary", notification -> Optional.empty(), false));
        assertThrows(IllegalArgumentException.class,
                () -> builder.registerProvider(channel, "secondary", notification -> Optional.empty(), true));
        builder.registerProvider(channel, "Primary", inbox2 -> Optional.of("new"), false);

        assertEquals(Optional.of("original"), client.send(new SendRequest(inbox)).providerMessageId());
        assertRouteFailure(client, new SendRequest(inbox, "Primary"), SendException.Reason.UNKNOWN_PROVIDER);
        assertEquals(Optional.of("new"), builder.build().send(new SendRequest(inbox, "Primary")).providerMessageId());
    }

    private static void assertRouteFailure(NotificationClient client, SendRequest request, SendException.Reason reason) {
        var failure = assertThrows(SendException.class, () -> client.send(request));
        assertEquals(reason, failure.reason());
        assertEquals(0, failure.attempts());
        assertEquals(Optional.empty(), failure.provider());
    }
}
