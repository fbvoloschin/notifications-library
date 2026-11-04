package dev.notifications;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Offline FCM HTTP v1 adapter. Reserved custom data keys are not modeled. */
public final class FcmProvider implements NotificationProvider<PushNotification> {
    private static final Pattern PROJECT_ID = Pattern.compile("[a-z][a-z0-9-]{4,28}[a-z0-9]");
    private final String projectId;
    private final String accessToken;
    private final SimulatedTransport transport;

    public FcmProvider(String projectId, String accessToken) {
        this(projectId, accessToken, request -> new SimulatedResponse(200, Map.of(),
                Map.of("name", "projects/" + projectId + "/messages/simulated-fcm")));
    }

    public FcmProvider(String projectId, String accessToken, SimulatedTransport transport) {
        this.projectId = projectId;
        this.accessToken = accessToken;
        this.transport = transport;
    }

    @Override
    public void validateConfiguration() {
        if (projectId == null || !PROJECT_ID.matcher(projectId).matches()
                || !ProviderSupport.hasText(accessToken) || transport == null) {
            throw new IllegalArgumentException("Invalid FCM configuration");
        }
    }

    @Override
    public Optional<String> send(PushNotification push) {
        var message = new HashMap<String, Object>();
        message.put("token", push.token());
        message.put("notification", Map.of("title", push.title(), "body", push.body()));
        if (!push.data().isEmpty()) {
            message.put("data", push.data());
        }
        var request = new SimulatedRequest("POST", "/v1/projects/" + projectId + "/messages:send",
                Map.of("Authorization", "Bearer " + accessToken, "Content-Type", "application/json"),
                Map.of("message", Map.copyOf(message)));
        var response = ProviderSupport.exchange(transport, request);
        if (response.status() != 200) {
            throw ProviderSupport.failure(response, Set.of(503));
        }
        return ProviderSupport.safeId(ProviderSupport.requireField(response, "name"), accessToken);
    }
}
