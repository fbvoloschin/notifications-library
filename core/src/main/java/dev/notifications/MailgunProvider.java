package dev.notifications;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Offline Mailgun Messages adapter; models multipart fields, not wire encoding. */
public final class MailgunProvider implements NotificationProvider<EmailNotification> {
    private final String apiKey;
    private final String domain;
    private final String sender;
    private final SimulatedTransport transport;

    public MailgunProvider(String apiKey, String domain, String sender) {
        this(apiKey, domain, sender, request -> new SimulatedResponse(200, Map.of(),
                Map.of("id", "<simulated-mailgun@" + domain + ">", "message", "Queued")));
    }

    public MailgunProvider(String apiKey, String domain, String sender, SimulatedTransport transport) {
        this.apiKey = apiKey;
        this.domain = domain;
        this.sender = sender;
        this.transport = transport;
    }

    @Override
    public void validateConfiguration() {
        if (!ProviderSupport.hasText(apiKey) || !NotificationValidators.validEmailAddress("x@" + domain)
                || !NotificationValidators.validEmailAddress(sender) || transport == null) {
            throw new IllegalArgumentException("Invalid Mailgun configuration");
        }
    }

    @Override
    public Optional<String> send(EmailNotification email) {
        var token = ProviderSupport.basicToken("api", apiKey);
        var request = new SimulatedRequest("POST", "/v3/" + domain + "/messages",
                Map.of("Authorization", "Basic " + token, "Content-Type", "multipart/form-data"),
                Map.of("from", sender, "to", email.recipient(), "subject", email.subject(), "text", email.body()));
        var response = ProviderSupport.exchange(transport, request);
        if (response.status() != 200) {
            throw ProviderSupport.failure(response, Set.of());
        }
        var id = ProviderSupport.requireField(response, "id");
        ProviderSupport.requireField(response, "message");
        return ProviderSupport.safeId(id, apiKey, token);
    }
}
