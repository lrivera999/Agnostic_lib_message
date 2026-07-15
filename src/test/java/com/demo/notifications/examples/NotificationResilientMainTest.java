package com.demo.notifications.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class NotificationResilientMainTest {

    @Test
    void executePrintsRetryAndFailoverTelemetry() {
        CapturedOutput output = capture(new String[] {
            "--channel=email",
            "--to=user@example.com",
            "--subject=Bienvenido",
            "--message=Tu cuenta esta lista.",
            "--demo-failover"
        });

        assertEquals(0, output.exitCode());
        assertTrue(output.stdout().contains("notification.provider.retry"));
        assertTrue(output.stdout().contains("notification.provider.failover"));
        assertTrue(output.stdout().contains("provider=Gmail"));
        assertTrue(output.stdout().contains("channel=EMAIL"));
        assertTrue(output.stdout().contains("success=true"));
    }

    @Test
    void executePrintsCircuitBreakerTelemetry() {
        CapturedOutput output = capture(new String[] {
            "--channel=email",
            "--to=user@example.com",
            "--subject=Bienvenido",
            "--message=Tu cuenta esta lista.",
            "--demo-circuit-breaker"
        });

        assertEquals(0, output.exitCode());
        assertTrue(output.stdout().contains("notification.provider.circuit_tripped"));
        assertTrue(output.stdout().contains("notification.provider.circuit_open"));
        assertTrue(output.stdout().contains("provider=Gmail"));
        assertTrue(output.stdout().contains("success=true"));
    }

    private static CapturedOutput capture(String[] args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = NotificationResilientMain.execute(
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
