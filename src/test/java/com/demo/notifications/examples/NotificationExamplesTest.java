package com.demo.notifications.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.enums.NotificationChannel;

class NotificationExamplesTest {

    @Test
    void executePrintsUsageWhenNoArgumentsAreProvided() {
        CapturedOutput output = capture(new String[0]);

        assertEquals(0, output.exitCode());
        assertTrue(output.stdout().contains("Uso:"));
        assertTrue(output.stderr().isBlank());
    }

    @Test
    void executePrintsAResultForAnEmailRequest() {
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
        assertTrue(output.stderr().isBlank());
    }

    @Test
    void parseSupportsShortFlags() {
        NotificationExamples.CliOptions options = NotificationExamples.CliOptions.parse(new String[] {
            "-c", "sms",
            "-t", "+573001234567",
            "-m", "Tu codigo es 123456"
        });

        assertFalse(options.showHelp());
        assertEquals("sms", options.channel());
        assertEquals("+573001234567", options.recipient());
        assertEquals("Tu codigo es 123456", options.message());

        NotificationRequest request = options.toRequest();
        assertEquals(NotificationChannel.SMS, request.channel());
        assertEquals("+573001234567", request.recipient());
        assertEquals("Tu codigo es 123456", request.message());
    }

    @Test
    void executeReturnsErrorForUnknownOptions() {
        CapturedOutput output = capture(new String[] {
            "--foo=bar",
            "--channel=email",
            "--to=user@example.com",
            "--subject=Bienvenido",
            "--message=Hola"
        });

        assertEquals(1, output.exitCode());
        assertTrue(output.stderr().contains("Argumento no reconocido"));
    }

    private static CapturedOutput capture(String[] args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = NotificationExamples.execute(
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
