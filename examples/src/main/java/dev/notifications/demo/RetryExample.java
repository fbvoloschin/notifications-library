package dev.notifications.demo;

import dev.notifications.NotificationClient;
import dev.notifications.ProviderFailure;
import dev.notifications.RetryPolicy;
import dev.notifications.SendRequest;
import dev.notifications.channels.EmailNotification;
import dev.notifications.channels.NotificationValidators;
import java.io.PrintStream;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Recovers from one controlled transient failure without waiting in real time. */
public final class RetryExample {
    private RetryExample() { }

    public static void main(String[] args) { run(System.out); }

    static void run(PrintStream out) {
        var calls = new AtomicInteger();
        var client = NotificationClient.builder()
                .registerChannel(EmailNotification.CHANNEL, NotificationValidators::validateEmail)
                .registerProvider(EmailNotification.CHANNEL, "simulated", email -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new ProviderFailure(ProviderFailure.Kind.TRANSIENT);
                    }
                    return Optional.of("demo-id");
                }, true)
                .retryPolicy(new RetryPolicy(2, Duration.ofSeconds(1), Duration.ofSeconds(1)))
                // Demo only: record the wait without sleeping. Real applications normally use the default.
                .retryWait(delay -> out.println("Simulated wait=" + delay))
                .build();
        var email = new EmailNotification("reader@example.com", "Hello", "Retry demo");
        var result = client.send(new SendRequest(email));
        out.println("ACCEPTED provider=" + result.provider() + " attempts=" + result.attempts());
    }
}
