package consumer;

import dev.notifications.Channel;
import dev.notifications.NotificationClient;
import dev.notifications.ProviderAcceptance;
import dev.notifications.SendRequest;
import dev.notifications.channels.EmailNotification;
import dev.notifications.channels.NotificationValidators;
import dev.notifications.channels.PushNotification;
import dev.notifications.channels.SmsNotification;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChannelPackageTest {
    @Test
    void consumerRegistersTheThreeBuiltInChannelsWithTheRootApi() {
        Channel<EmailNotification> email = EmailNotification.CHANNEL;
        Channel<SmsNotification> sms = SmsNotification.CHANNEL;
        Channel<PushNotification> push = PushNotification.CHANNEL;
        var client = NotificationClient.builder()
                .registerChannel(email, NotificationValidators::validateEmail)
                .registerProvider(email, "email-stub", notification -> Optional.of(notification.subject()), true)
                .registerChannel(sms, NotificationValidators::validateSms)
                .registerProvider(sms, "sms-stub", notification -> Optional.of(notification.body()), true)
                .registerChannel(push, NotificationValidators::validatePush)
                .registerProvider(push, "push-stub", notification -> Optional.of(notification.title()), true)
                .build();

        var accepted = List.of(
                new SendRequest(new EmailNotification("dev@example.com", "email-subject", "Message")),
                new SendRequest(new SmsNotification("+15551234567", "sms-body")),
                new SendRequest(new PushNotification("device-token", "push-title", "Message")))
                .stream().map(client::send).toList();

        assertEquals(List.of("email", "sms", "push"), accepted.stream().map(ProviderAcceptance::channel).toList());
        assertEquals(List.of("email-stub", "sms-stub", "push-stub"),
                accepted.stream().map(ProviderAcceptance::provider).toList());
        assertEquals(List.of("email-subject", "sms-body", "push-title"),
                accepted.stream().map(result -> result.providerMessageId().orElseThrow()).toList());
    }
}
