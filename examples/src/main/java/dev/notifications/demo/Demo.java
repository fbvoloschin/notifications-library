package dev.notifications.demo;

import dev.notifications.NotificationClient;
import dev.notifications.ProviderAcceptance;
import dev.notifications.SendRequest;
import dev.notifications.channels.EmailNotification;
import dev.notifications.channels.NotificationValidators;
import dev.notifications.channels.PushNotification;
import dev.notifications.channels.SmsNotification;
import dev.notifications.providers.FcmProvider;
import dev.notifications.providers.MailgunProvider;
import dev.notifications.providers.SendGridProvider;
import dev.notifications.providers.TwilioProvider;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Standalone offline demo of the built-in Email, SMS, and Push channels. */
public final class Demo {
    private Demo() { }

    public static void main(String[] args) {
        run(System.out);
    }

    static void run(PrintStream output) {
        Objects.requireNonNull(output, "Output is required");
        var client = NotificationClient.builder()
                .registerChannel(EmailNotification.CHANNEL, NotificationValidators::validateEmail)
                .registerProvider(EmailNotification.CHANNEL, "sendgrid",
                        new SendGridProvider("demo-sendgrid-key", "notifications@example.com"), true)
                .registerProvider(EmailNotification.CHANNEL, "mailgun",
                        new MailgunProvider("demo-mailgun-key", "mg.example.com", "notifications@example.com"), false)
                .registerChannel(SmsNotification.CHANNEL, NotificationValidators::validateSms)
                .registerProvider(SmsNotification.CHANNEL, "twilio",
                        new TwilioProvider("AC" + "0123456789abcdef" + "0123456789abcdef",
                                "demo-twilio-token", "+15550001111"), true)
                .registerChannel(PushNotification.CHANNEL, NotificationValidators::validatePush)
                .registerProvider(PushNotification.CHANNEL, "fcm",
                        new FcmProvider("demo-project", "demo-fcm-access-token"), true)
                .build();

        var requests = List.of(
                new SendRequest(new EmailNotification("reviewer@example.com", "Build complete", "Ready for review")),
                new SendRequest(new SmsNotification("+15551234567", "Build complete")),
                new SendRequest(new PushNotification("demo-device-token", "Build complete", "Ready for review",
                        Map.of("kind", "demo"))));

        for (var request : requests) {
            printAcceptance(output, client.send(request));
        }
    }

    private static void printAcceptance(PrintStream output, ProviderAcceptance acceptance) {
        output.printf("ACCEPTED channel=%s provider=%s attempts=%d%n",
                acceptance.channel(), acceptance.provider(), acceptance.attempts());
    }
}
