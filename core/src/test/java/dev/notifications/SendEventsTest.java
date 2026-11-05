package dev.notifications;

import dev.notifications.channels.EmailNotification;
import dev.notifications.channels.NotificationValidators;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class SendEventsTest {
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final SendRequest REQUEST = new SendRequest(new EmailNotification("dev@example.com", "Subject", "Body"));
    private static final RetryPolicy RETRY = new RetryPolicy(2, Duration.ofSeconds(1), Duration.ofSeconds(4));

    @Test
    void acceptedEventsHaveCorrelatedIdsAndOrderedSnapshottedListeners() {
        var order = new ArrayList<String>();
        var events = new ArrayList<SendEvent>();
        var builder = builder(email -> Optional.empty())
                .addListener(event -> {
                    order.add("first");
                    throw new IllegalStateException("private listener details");
                })
                .addListener(event -> {
                    order.add("second");
                    events.add(event);
                });
        var client = builder.build();
        builder.addListener(event -> fail("A built client must keep its listener snapshot"));

        var first = client.send(REQUEST);
        var second = client.send(REQUEST);

        assertEquals(List.of("first", "second", "first", "second"), order);
        assertEquals(List.of(first.correlationId(), second.correlationId()),
                events.stream().map(SendEvent::correlationId).toList());
        assertNotEquals(first.correlationId(), second.correlationId());
        for (var event : events) {
            assertEquals(SendEvent.Type.ACCEPTED, event.type());
            assertEquals(NOW, event.timestamp());
            assertEquals("email", event.channel());
            assertEquals(Optional.of("provider"), event.provider());
            assertEquals(1, event.attempts());
            assertEquals(Optional.empty(), event.reason());
        }
    }

    @Test
    void retryingIsPublishedAfterTheWaitAndImmediatelyBeforeTheNextAttempt() {
        var trace = new ArrayList<String>();
        var events = new ArrayList<SendEvent>();
        var calls = new AtomicInteger();
        var client = builder(email -> {
            var attempt = calls.incrementAndGet();
            trace.add("attempt " + attempt);
            if (attempt == 1) {
                throw new ProviderFailure(ProviderFailure.Kind.TRANSIENT);
            }
            return Optional.of("accepted");
        }).retryPolicy(RETRY).retryWait(delay -> trace.add("wait " + delay.toSeconds()))
                .addListener(event -> { throw new IllegalStateException("ignored listener failure"); })
                .addListener(event -> {
                    events.add(event);
                    trace.add(event.type().name());
                }).build();

        var acceptance = client.send(REQUEST);

        assertEquals(List.of("attempt 1", "wait 1", "RETRYING", "attempt 2", "ACCEPTED"), trace);
        assertEquals(List.of(1, 2), events.stream().map(SendEvent::attempts).toList());
        assertEquals(List.of(acceptance.correlationId(), acceptance.correlationId()),
                events.stream().map(SendEvent::correlationId).toList());
        assertEquals(List.of(Optional.of("provider"), Optional.of("provider")),
                events.stream().map(SendEvent::provider).toList());
    }

    @Test
    void anInterruptedWaitEmitsOnlyOneSafeTerminalFailure() {
        var events = new ArrayList<SendEvent>();
        var client = builder(email -> { throw new ProviderFailure(ProviderFailure.Kind.TRANSIENT); })
                .retryPolicy(RETRY).retryWait(delay -> { throw new InterruptedException("private wait details"); })
                .addListener(events::add).build();

        SendException failure;
        try {
            failure = assertThrows(SendException.class, () -> client.send(REQUEST));
        } finally {
            Thread.interrupted();
        }

        assertEquals(1, events.size());
        var event = events.getFirst();
        assertEquals(SendEvent.Type.FAILED, event.type());
        assertEquals(Optional.of(SendException.Reason.INTERRUPTED), event.reason());
        assertEquals(1, event.attempts());
        assertEquals(Optional.of("provider"), event.provider());
        assertEquals(failure.correlationId(), event.correlationId());
        assertFalse(event.toString().contains("private wait details"));
    }

    @Test
    void aRoutingFailurePublishesNoSelectedProviderOrAttempts() {
        var events = new ArrayList<SendEvent>();
        var client = builder(email -> fail("Unknown provider must not fall back"))
                .addListener(events::add).build();

        var failure = assertThrows(SendException.class,
                () -> client.send(new SendRequest(REQUEST.notification(), "unknown")));

        assertEquals(List.of(new SendEvent(SendEvent.Type.FAILED, failure.correlationId(), NOW, "email",
                Optional.empty(), 0, Optional.of(SendException.Reason.UNKNOWN_PROVIDER))), events);
    }

    private static NotificationClientBuilder builder(NotificationProvider<EmailNotification> provider) {
        return NotificationClient.builder().clock(Clock.fixed(NOW, ZoneOffset.UTC))
                .registerChannel(EmailNotification.CHANNEL, NotificationValidators::validateEmail)
                .registerProvider(EmailNotification.CHANNEL, "provider", provider, true);
    }
}
