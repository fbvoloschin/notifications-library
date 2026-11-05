package dev.notifications.demo;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdditionalExamplesTest {
    private String outputOf(Consumer<PrintStream> example) {
        var bytes = new ByteArrayOutputStream();
        try (var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            example.accept(output);
        }
        return bytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test void switchesProviders() {
        assertEquals("ACCEPTED provider=sendgrid\nACCEPTED provider=mailgun\n", outputOf(ProviderSwitchExample::run));
    }

    @Test void continuesBeforeAsyncSendCompletes() {
        assertEquals("Caller continues; send pending=true\nACCEPTED provider=simulated\n", outputOf(AsyncExample::run));
    }

    @Test void retriesTransientFailureWithoutRealWaiting() {
        assertEquals("Simulated wait=PT1S\nACCEPTED provider=simulated attempts=2\n", outputOf(RetryExample::run));
    }

    @Test void registersCustomChannel() {
        assertEquals("ACCEPTED channel=inbox provider=local\n", outputOf(CustomChannelExample::run));
    }
}
