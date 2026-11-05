package dev.notifications.demo;

import dev.notifications.Channel;
import dev.notifications.Notification;
import dev.notifications.NotificationClient;
import dev.notifications.SendRequest;
import java.io.PrintStream;
import java.util.List;
import java.util.Optional;

/** Adds a typed channel and a provider without editing the library. */
public final class CustomChannelExample {
    private CustomChannelExample() { }

    private record InboxNotification(String text) implements Notification {
        private static final Channel<InboxNotification> CHANNEL = new Channel<>("inbox", InboxNotification.class);
        @Override public Channel<InboxNotification> channel() { return CHANNEL; }
    }

    public static void main(String[] args) { run(System.out); }

    static void run(PrintStream out) {
        var client = NotificationClient.builder()
                .registerChannel(InboxNotification.CHANNEL,
                        inbox -> inbox.text() == null || inbox.text().isBlank()
                                ? List.of("text is required") : List.of())
                .registerProvider(InboxNotification.CHANNEL, "local", inbox -> Optional.of("demo-id"), true)
                .build();
        var result = client.send(new SendRequest(new InboxNotification("Hello inbox")));
        out.println("ACCEPTED channel=" + result.channel() + " provider=" + result.provider());
    }
}
