package dev.notifications.demo;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoTest {
    @Test
    void runsOfflineExamplesForEmailSmsAndPush() {
        var output = new ByteArrayOutputStream();

        Demo.run(new PrintStream(output, true, StandardCharsets.UTF_8));

        var lines = output.toString(StandardCharsets.UTF_8).lines().toList();
        assertEquals(3, lines.size());
        assertTrue(lines.contains("ACCEPTED channel=email provider=sendgrid attempts=1"));
        assertTrue(lines.contains("ACCEPTED channel=sms provider=twilio attempts=1"));
        assertTrue(lines.contains("ACCEPTED channel=push provider=fcm attempts=1"));
    }
}
