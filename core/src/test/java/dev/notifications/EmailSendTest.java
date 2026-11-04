package dev.notifications;

import dev.notifications.channels.EmailNotification;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailSendTest {
    @Test
    void sendsEmailThroughTheConfiguredDefaultAndReportsAcceptance() {
        var email = new EmailNotification("reader@example.com", "Build complete", "Ready for review");
        var seen = new AtomicReference<EmailNotification>();
        var client = NotificationClient.builder()
                .registerChannel(EmailNotification.CHANNEL, notification -> List.of())
                .registerProvider(EmailNotification.CHANNEL, "primary", notification -> {
                    seen.set(notification);
                    return Optional.of("accepted-123");
                }, true)
                .build();

        var acceptance = client.send(new SendRequest(email));

        assertSame(email, seen.get());
        assertEquals("email", acceptance.channel());
        assertEquals("primary", acceptance.provider());
        assertEquals(1, acceptance.attempts());
        assertEquals(Optional.of("accepted-123"), acceptance.providerMessageId());
    }

    @Test
    void explicitProviderWorksWithoutDefaultAndDoesNotInventAMessageId() {
        var client = NotificationClient.builder()
                .registerChannel(EmailNotification.CHANNEL, notification -> List.of())
                .registerProvider(EmailNotification.CHANNEL, "backup", notification -> Optional.empty(), false)
                .build();
        var email = new EmailNotification("another@example.com", "Hello", "Second message");

        var acceptance = client.send(new SendRequest(email, "backup"));

        assertEquals("backup", acceptance.provider());
        assertEquals(Optional.empty(), acceptance.providerMessageId());
    }

    @Test
    void aNamedProviderIsNotUsedAsAnImplicitDefault() {
        var client = NotificationClient.builder()
                .registerChannel(EmailNotification.CHANNEL, notification -> List.of())
                .registerProvider(EmailNotification.CHANNEL, "backup", notification -> {
                    throw new AssertionError("No provider should be invoked");
                }, false)
                .build();
        var request = new SendRequest(new EmailNotification("another@example.com", "Hello", "Second message"));

        var failure = assertThrows(SendException.class, () -> client.send(request));

        assertEquals(SendException.Reason.MISSING_DEFAULT, failure.reason());
        assertEquals(0, failure.attempts());
        assertEquals(Optional.empty(), failure.provider());
    }
}
