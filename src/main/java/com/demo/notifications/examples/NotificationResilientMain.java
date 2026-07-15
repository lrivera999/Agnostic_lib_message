package com.demo.notifications.examples;

import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.NotificationResult;
import com.demo.notifications.core.enums.NotificationChannel;
import com.demo.notifications.core.exceptions.NotificationException;
import com.demo.notifications.core.interfaces.NotificationSender;
import com.demo.notifications.observability.NotificationTelemetryObservation;
import com.demo.notifications.observability.NotificationTelemetryPort;
import com.demo.notifications.observability.NotificationTelemetryScope;
import com.demo.notifications.providers.email.impl.GmailEmailSender;
import com.demo.notifications.providers.email.impl.GridEmailSender;
import com.demo.notifications.providers.email.impl.MailgunEmailSender;
import com.demo.notifications.providers.push.impl.FirebasePushSender;
import com.demo.notifications.providers.sms.impl.TwilioSmsSender;
import com.demo.notifications.services.NotificationResiliencePolicy;
import com.demo.notifications.services.NotificationService;
import com.demo.notifications.services.NotificationServiceBuilder;

public final class NotificationResilientMain {

    private static final String DEMO_FAILOVER_FLAG = "--demo-failover";
    private static final String DEMO_CIRCUIT_BREAKER_FLAG = "--demo-circuit-breaker";

    private static final String DEMO_USAGE = """
        Opciones del demo resiliente:
          --demo-failover            Fuerza al proveedor primario a fallar para mostrar retry y fallback hacia Gmail.
          --demo-circuit-breaker     Ejecuta dos envios para mostrar el breaker abierto y el salto directo a Gmail.
        """;

    private NotificationResilientMain() {
    }

    public static void main(String[] args) {
        int exitCode = execute(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int execute(String[] args, PrintStream out, PrintStream err) {
        DemoOptions demoOptions = DemoOptions.parse(args);

        NotificationExamples.CliOptions options;
        try {
            options = NotificationExamples.CliOptions.parse(demoOptions.notificationArgs());
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            err.println(NotificationExamples.USAGE);
            err.println(DEMO_USAGE);
            return 1;
        }

        if (options.showHelp()) {
            out.println(NotificationExamples.USAGE);
            out.println(DEMO_USAGE);
            return 0;
        }

        NotificationRequest request;
        try {
            request = options.toRequest();
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            err.println(NotificationExamples.USAGE);
            err.println(DEMO_USAGE);
            return 1;
        }

        try (ExecutorService executor = Executors.newFixedThreadPool(
            4,
            Thread.ofPlatform().name("notifications-resilient-", 0).factory())) {
            NotificationService service = buildService(executor, out, demoOptions.mode());
            if (demoOptions.mode() == DemoMode.CIRCUIT_BREAKER) {
                out.println("[demo] envio 1");
                NotificationResult first = service.sendAsync(request).join();
                out.println(first);

                out.println("[demo] envio 2");
                NotificationResult second = service.sendAsync(request).join();
                out.println(second);
            } else {
                NotificationResult result = service.sendAsync(request).join();
                out.println(result);
            }
            return 0;
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            err.println(cause.getMessage());
            err.println(NotificationExamples.USAGE);
            err.println(DEMO_USAGE);
            return 1;
        }
    }

    static NotificationService buildService(ExecutorService executor) {
        return buildService(executor, System.out, DemoMode.NONE);
    }

    static NotificationService buildService(ExecutorService executor, PrintStream out, DemoMode mode) {
        NotificationResiliencePolicy emailPolicy = resiliencePolicy(mode);

        NotificationSender primaryEmailSender = mode == DemoMode.NONE
            ? new GridEmailSender(
                "demo-sendgrid-api-key",
                "demo.mail.example",
                "noreply@demo.mail.example")
            : new AlwaysFailingNotificationSender(
                NotificationChannel.EMAIL,
                "SendGrid",
                "Fallo simulado del proveedor primario SendGrid para mostrar retry, fallback y circuit breaker");

        return new NotificationServiceBuilder()
            .executor(executor)
            .telemetry(new ConsoleNotificationTelemetryPort(out))
            .register(primaryEmailSender)
            .register(new GmailEmailSender(
                "demo-gmail-username",
                "demo-gmail-app-password",
                "noreply@demo.mail.example"))
            .register(new MailgunEmailSender(
                "demo-mailgun-api-key",
                "demo.mail.example",
                "noreply@demo.mail.example"))
            .register(new TwilioSmsSender(
                "demo-twilio-token",
                "AC1234567890",
                "+15551234567"))
            .register(new FirebasePushSender(
                "demo-firebase-api-key",
                "demo-project",
                "service-account.json"))
            .resilience(NotificationChannel.EMAIL, "SendGrid", emailPolicy)
            .resilience(NotificationChannel.EMAIL, "Gmail", emailPolicy)
            .resilience(NotificationChannel.EMAIL, "Mailgun", emailPolicy)
            .fallbackProviders(NotificationChannel.EMAIL, "SendGrid", "Gmail", "Mailgun")
            .build();
    }

    private static NotificationResiliencePolicy resiliencePolicy(DemoMode mode) {
        if (mode == DemoMode.CIRCUIT_BREAKER) {
            return NotificationResiliencePolicy.builder()
                .retry(1, Duration.ZERO, 1.0d, Duration.ZERO)
                .circuitBreaker(1, Duration.ofSeconds(10), 1)
                .rateLimit(20, Duration.ofSeconds(1))
                .timeout(Duration.ofSeconds(2))
                .build();
        }

        return NotificationResiliencePolicy.builder()
            .retry(3, Duration.ofMillis(75), 2.0d, Duration.ofMillis(300))
            .circuitBreaker(10, Duration.ofSeconds(10), 1)
            .rateLimit(20, Duration.ofSeconds(1))
            .timeout(Duration.ofSeconds(2))
            .build();
    }

    private enum DemoMode {
        NONE,
        FAILOVER,
        CIRCUIT_BREAKER
    }

    private record DemoOptions(DemoMode mode, String[] notificationArgs) {

        private static DemoOptions parse(String[] args) {
            if (args == null || args.length == 0) {
                return new DemoOptions(DemoMode.NONE, new String[0]);
            }

            List<String> forwarded = new ArrayList<>(args.length);
            boolean forceFailover = false;
            boolean forceCircuitBreaker = false;

            for (String token : args) {
                if (token == null || token.isBlank()) {
                    continue;
                }

                if (isFlagEnabled(token, DEMO_FAILOVER_FLAG)) {
                    forceFailover = true;
                    continue;
                }

                if (isFlagEnabled(token, DEMO_CIRCUIT_BREAKER_FLAG)) {
                    forceCircuitBreaker = true;
                    continue;
                }

                forwarded.add(token);
            }

            DemoMode mode = forceCircuitBreaker
                ? DemoMode.CIRCUIT_BREAKER
                : forceFailover ? DemoMode.FAILOVER : DemoMode.NONE;

            return new DemoOptions(mode, forwarded.toArray(String[]::new));
        }
    }

    private static boolean isFlagEnabled(String token, String flag) {
        if (flag.equals(token)) {
            return true;
        }

        if (token.startsWith(flag + "=")) {
            return Boolean.parseBoolean(token.substring(flag.length() + 1));
        }

        return false;
    }

    private static final class ConsoleNotificationTelemetryPort implements NotificationTelemetryPort {
        private final PrintStream out;

        private ConsoleNotificationTelemetryPort(PrintStream out) {
            this.out = out;
        }

        @Override
        public NotificationTelemetryScope start(NotificationTelemetryObservation observation) {
            out.println(formatStart(observation));
            return new ConsoleNotificationTelemetryScope(out);
        }

        @Override
        public <T> Supplier<T> contextualize(Supplier<T> task) {
            return task;
        }

        @Override
        public Runnable contextualize(Runnable task) {
            return task;
        }
    }

    private static final class ConsoleNotificationTelemetryScope implements NotificationTelemetryScope {
        private final PrintStream out;

        private ConsoleNotificationTelemetryScope(PrintStream out) {
            this.out = out;
        }

        @Override
        public void attribute(String key, String value) {
            out.println("[resilience] attr " + key + "=" + value);
        }

        @Override
        public void event(String name, Map<String, String> attributes) {
            out.println("[resilience] event " + name + formatAttributes(attributes));
        }

        @Override
        public void success(String resultId) {
            out.println("[resilience] success resultId=" + resultId);
        }

        @Override
        public void failure(String errorType, String message) {
            out.println("[resilience] failure type=" + errorType + " message=" + message);
        }

        @Override
        public void close() {
            out.println("[resilience] close");
        }
    }

    private static final class AlwaysFailingNotificationSender implements NotificationSender {
        private final NotificationChannel channel;
        private final String provider;
        private final String failureMessage;

        private AlwaysFailingNotificationSender(
            NotificationChannel channel,
            String provider,
            String failureMessage) {
            this.channel = channel;
            this.provider = provider;
            this.failureMessage = failureMessage;
        }

        @Override
        public NotificationChannel channel() {
            return channel;
        }

        @Override
        public String provider() {
            return provider;
        }

        @Override
        public NotificationResult send(NotificationRequest request) {
            throw new NotificationException(failureMessage);
        }
    }

    private static String formatStart(NotificationTelemetryObservation observation) {
        return "[resilience] start span="
            + observation.spanName()
            + " channel="
            + observation.channel()
            + " provider="
            + (observation.provider() == null ? "none" : observation.provider())
            + " candidates="
            + observation.candidateCount()
            + " async="
            + observation.async()
            + " batch="
            + observation.batch();
    }

    private static String formatAttributes(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return "";
        }

        return " "
            + attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(" "));
    }
}
