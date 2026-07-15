package com.demo.notifications.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class NotificationMultichannelMainTest {

    @Test
    void executePrintsResultsForEmailSmsAndPush() {
        CapturedOutput output = capture(new String[0]);

        assertEquals(0, output.exitCode());
        assertTrue(output.stdout().contains("[multicanal] enviando 3 notificaciones en paralelo"));
        assertTrue(output.stdout().contains("channel=EMAIL"));
        assertTrue(output.stdout().contains("channel=SMS"));
        assertTrue(output.stdout().contains("channel=PUSH"));
        assertTrue(output.stdout().contains("provider=SendGrid"));
        assertTrue(output.stdout().contains("provider=Twilio"));
        assertTrue(output.stdout().contains("provider=Firebase"));
    }

    @Test
    void executePrintsUsageWhenHelpIsRequested() {
        CapturedOutput output = capture(new String[] {"--help"});

        assertEquals(0, output.exitCode());
        assertTrue(output.stdout().contains("Uso:"));
        assertTrue(output.stdout().contains("NotificationMultichannelMain"));
    }

    private static CapturedOutput capture(String[] args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = NotificationMultichannelMain.execute(
            args,
            new PrintStream(stdout, true, StandardCharsets.UTF_8),
            new PrintStream(stderr, true, StandardCharsets.UTF_8));
        return new CapturedOutput(
            exitCode,
            stdout.toString(StandardCharsets.UTF_8),
            stderr.toString(StandardCharsets.UTF_8));
    }

    private record CapturedOutput(int exitCode, String stdout, String stderr) {
    }
}
