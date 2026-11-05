package dev.notifications.demo;

import dev.notifications.NotificationClient;
import dev.notifications.SendRequest;
import dev.notifications.channels.EmailNotification;
import dev.notifications.channels.NotificationValidators;
import java.io.PrintStream;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/** Shows the caller continuing while a simulated provider is still waiting. */
public final class AsyncExample {
    private AsyncExample() { }

    public static void main(String[] args) { run(System.out); }

    static void run(PrintStream out) {
        var releaseSend = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var client = NotificationClient.builder()
                    .executor(executor)
                    .registerChannel(EmailNotification.CHANNEL, NotificationValidators::validateEmail)
                    .registerProvider(EmailNotification.CHANNEL, "simulated", email -> {
                        try {
                            releaseSend.await(); // Demo only: hold the provider until the caller continues.
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(interrupted);
                        }
                        return Optional.of("demo-id");
                    }, true)
                    .build();
            var email = new EmailNotification("reader@example.com", "Hello", "Async demo");
            var future = client.sendAsync(new SendRequest(email));
            try {
                out.println("Caller continues; send pending=" + !future.isDone());
            } finally {
                releaseSend.countDown();
            }
            out.println("ACCEPTED provider=" + future.join().provider());
        }
    }
}
