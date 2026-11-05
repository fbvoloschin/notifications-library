package dev.notifications.providers;

import dev.notifications.NotificationProvider;
import dev.notifications.channels.EmailNotification;
import dev.notifications.channels.NotificationValidators;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Offline model of SendGrid Mail Send. No SDK, network calls, or credential discovery. */
public final class SendGridProvider implements NotificationProvider<EmailNotification> {
    private final String apiKey;
    private final String sender;
    private final SimulatedTransport transport;

    public SendGridProvider(String apiKey, String sender) {
        this(apiKey, sender, request -> new SimulatedResponse(202));
    }

    public SendGridProvider(String apiKey, String sender, SimulatedTransport transport) {
        this.apiKey = apiKey;
        this.sender = sender;
        this.transport = transport;
    }

    @Override
    public void validateConfiguration() {
        if (apiKey == null || apiKey.isBlank() || !NotificationValidators.validEmailAddress(sender) || transport == null) {
            throw new IllegalArgumentException("Invalid SendGrid configuration");
        }
    }

    @Override
    public Optional<String> send(EmailNotification email) {
        var request = new SimulatedRequest("POST", "/v3/mail/send",
                Map.of("Authorization", "Bearer " + apiKey, "Content-Type", "application/json"), Map.of(
                        "personalizations", List.of(Map.of("to", List.of(Map.of("email", email.recipient())))),
                        "from", Map.of("email", sender),
                        "subject", email.subject(),
                        "content", List.of(Map.of("type", "text/plain", "value", email.body()))));
        var response = ProviderSupport.exchange(transport, request);
        if (response.status() != 202 || !response.body().isEmpty()) {
            throw ProviderSupport.failure(response, Set.of(429));
        }
        return ProviderSupport.safeId(response.header("X-Message-ID").orElse(null), apiKey);
    }
}
