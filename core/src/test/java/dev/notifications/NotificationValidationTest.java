package dev.notifications;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class NotificationValidationTest {
    @Test
    void invalidEmailReportsEveryViolationBeforeProviderSelection() {
        var client = client(EmailNotification.CHANNEL, NotificationValidators::validateEmail);
        var request = new SendRequest(new EmailNotification("a..b@host.example", " ", null), "not-registered");

        assertInvalid(client, request, List.of("recipient", "subject", "body"));
    }

    @Test
    void emailAcceptsAnAddressWithAPlusTag() {
        assertEquals(List.of(), NotificationValidators.validateEmail(
                new EmailNotification("a.b+tag@sub.example.org", "Subject", "Body")));
    }

    @Test
    void smsUsesAsciiE164AndCountsUnicodeCodePoints() {
        var client = client(SmsNotification.CHANNEL, NotificationValidators::validateSms);
        var body = "😀".repeat(1600);
        assertEquals(List.of(), NotificationValidators.validateSms(new SmsNotification("+12345678", body)));
        assertEquals(List.of(), NotificationValidators.validateSms(new SmsNotification("+123456789012345", body)));

        assertInvalid(client, new SendRequest(new SmsNotification("+01234567", body + "😀")),
                List.of("recipient", "body"));
        for (var number : List.of("12345678", "+1234567", "+1234567890123456", "+1234567١")) {
            assertEquals(List.of("recipient"), NotificationValidators.validateSms(new SmsNotification(number, "Hi")));
        }
        assertEquals(List.of("body"), NotificationValidators.validateSms(new SmsNotification("+12345678", " ")));
    }

    @Test
    void pushDataIsSnapshottedAndOptional() {
        var source = new HashMap<>(Map.of("kind", "news"));
        var push = new PushNotification("token", "Title", "Body", source);
        source.put("kind", "changed");

        assertEquals(Map.of("kind", "news"), push.data());
        assertThrows(UnsupportedOperationException.class, () -> push.data().clear());
        assertEquals(List.of(), NotificationValidators.validatePush(push));
        assertEquals(List.of(), NotificationValidators.validatePush(new PushNotification("token", "Title", "Body")));
    }

    @Test
    void invalidPushReportsInvalidDataAlongWithOtherFields() {
        var client = client(PushNotification.CHANNEL, NotificationValidators::validatePush);
        var withNullValue = new HashMap<String, String>();
        withNullValue.put("invalid", null);
        var withNullKey = new HashMap<String, String>();
        withNullKey.put(null, "invalid");
        @SuppressWarnings("unchecked")
        var withNonStringValue = (Map<String, String>) (Map<?, ?>) Map.of("kind", 42);
        for (var data : List.of(withNullKey, withNullValue, withNonStringValue)) {
            var request = new SendRequest(new PushNotification("", null, " ", data));
            assertInvalid(client, request, List.of("token", "title", "body", "data"));
        }
    }

    @Test
    void throwingValidatorIsUnexpectedAndDoesNotExposeItsMessage() {
        var client = client(EmailNotification.CHANNEL, notification -> {
            throw new IllegalStateException("private-validator-value");
        });
        var request = new SendRequest(new EmailNotification("a@b.example", "Subject", "Body"));

        var failure = assertThrows(SendException.class, () -> client.send(request));

        assertEquals(SendException.Reason.UNEXPECTED_VALIDATION, failure.reason());
        assertEquals(0, failure.attempts());
        assertEquals(Optional.empty(), failure.provider());
        assertEquals(List.of(), failure.violations());
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("private-validator-value"));
    }

    private static <N extends Notification> NotificationClient client(
            Channel<N> channel, NotificationValidator<N> validator) {
        return NotificationClient.builder().registerChannel(channel, validator)
                .registerProvider(channel, "forbidden", notification -> fail("Provider must not run"), true)
                .build();
    }

    private static void assertInvalid(NotificationClient client, SendRequest request, List<String> expected) {
        var failure = assertThrows(SendException.class, () -> client.send(request));
        assertEquals(SendException.Reason.INVALID_NOTIFICATION, failure.reason());
        assertEquals(expected, failure.violations());
        assertEquals(0, failure.attempts());
        assertEquals(Optional.empty(), failure.provider());
    }
}
