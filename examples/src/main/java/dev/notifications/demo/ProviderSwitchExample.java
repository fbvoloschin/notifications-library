package dev.notifications.demo;

import dev.notifications.NotificationClient;
import dev.notifications.SendRequest;
import dev.notifications.channels.EmailNotification;
import dev.notifications.channels.NotificationValidators;
import dev.notifications.providers.MailgunProvider;
import dev.notifications.providers.SendGridProvider;
import java.io.PrintStream;

/** Selects different simulated providers without changing the send operation. */
public final class ProviderSwitchExample {
    private ProviderSwitchExample() { }

    public static void main(String[] args) { run(System.out); }

    static void run(PrintStream out) {
        var client = NotificationClient.builder()
                .registerChannel(EmailNotification.CHANNEL, NotificationValidators::validateEmail)
                .registerProvider(EmailNotification.CHANNEL, "sendgrid",
                        new SendGridProvider("demo-key", "sender@example.com"), true)
                .registerProvider(EmailNotification.CHANNEL, "mailgun",
                        new MailgunProvider("demo-key", "example.com", "sender@example.com"), false)
                .build();
        var email = new EmailNotification("reader@example.com", "Hello", "Provider switch demo");
        for (var provider : new String[] {"sendgrid", "mailgun"}) {
            var result = client.send(new SendRequest(email, provider));
            out.println("ACCEPTED provider=" + result.provider());
        }
    }
}
