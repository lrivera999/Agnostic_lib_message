package com.demo.notifications.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class NotificationObservabilityMainTest {

    @Test
    void executePrintsAResultWithTheTelemetryAdapterWiredIn() {
        CapturedOutput output = capture(new String[] {
            "--channel=email",
            "--to=user@example.com",
            "--subject=Bienvenido",
            "--message=Tu cuenta esta lista."
        });

        assertEquals(0, output.exitCode());
        assertTrue(output.stdout().contains("channel=EMAIL"));
        assertTrue(output.stdout().contains("provider=SendGrid"));
        assertTrue(output.stdout().contains("success=true"));
    }

    private static CapturedOutput capture(String[] args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = NotificationObservabilityMain.execute(
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
