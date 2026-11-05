package dev.notifications.providers;

import dev.notifications.NotificationProvider;
import dev.notifications.channels.NotificationValidators;
import dev.notifications.channels.SmsNotification;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Offline Twilio Messages adapter for explicitly configured E.164 senders. */
public final class TwilioProvider implements NotificationProvider<SmsNotification> {
    private static final Pattern ACCOUNT_SID = Pattern.compile("AC[0-9a-fA-F]{32}");
    private final String accountSid;
    private final String authToken;
    private final String sender;
    private final SimulatedTransport transport;

    public TwilioProvider(String accountSid, String authToken, String sender) {
        this(accountSid, authToken, sender, request -> new SimulatedResponse(201, Map.of(),
                Map.of("sid", "SM00000000000000000000000000000000", "status", "queued")));
    }

    public TwilioProvider(String accountSid, String authToken, String sender, SimulatedTransport transport) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.sender = sender;
        this.transport = transport;
    }

    @Override
    public void validateConfiguration() {
        if (accountSid == null || !ACCOUNT_SID.matcher(accountSid).matches() || !ProviderSupport.hasText(authToken)
                || !NotificationValidators.validPhoneNumber(sender) || transport == null) {
            throw new IllegalArgumentException("Invalid Twilio configuration");
        }
    }

    @Override
    public Optional<String> send(SmsNotification sms) {
        var token = ProviderSupport.basicToken(accountSid, authToken);
        var request = new SimulatedRequest("POST", "/2010-04-01/Accounts/" + accountSid + "/Messages.json",
                Map.of("Authorization", "Basic " + token, "Content-Type", "application/x-www-form-urlencoded"),
                Map.of("To", sms.recipient(), "From", sender, "Body", sms.body()));
        var response = ProviderSupport.exchange(transport, request);
        if (response.status() != 201) {
            throw ProviderSupport.failure(response, Set.of(429));
        }
        var id = ProviderSupport.requireField(response, "sid");
        ProviderSupport.requireField(response, "status");
        return ProviderSupport.safeId(id, accountSid, authToken, token);
    }
}
