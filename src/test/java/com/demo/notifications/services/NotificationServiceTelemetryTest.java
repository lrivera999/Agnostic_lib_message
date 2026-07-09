package com.demo.notifications.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.NotificationResult;
import com.demo.notifications.core.enums.NotificationChannel;
import com.demo.notifications.core.interfaces.NotificationSender;
import com.demo.notifications.observability.NotificationTelemetryObservation;
import com.demo.notifications.observability.NotificationTelemetryPort;
import com.demo.notifications.observability.NotificationTelemetryScope;

class NotificationServiceTelemetryTest {

    @Test
    void sendRecordsTelemetryForASuccessfulRequest() {
        RecordingTelemetry telemetry = new RecordingTelemetry();
        RecordingSender sender = new RecordingSender(NotificationChannel.EMAIL, "SendGrid");

        NotificationService service = new NotificationServiceBuilder()
            .executor(Runnable::run)
            .telemetry(telemetry)
            .register(sender)
            .build();

        NotificationResult result = service.send(NotificationRequest.email(
            "user@example.com",
            "Welcome",
            "Body"));

        assertTrue(result.success());
        assertEquals(1, telemetry.scopes.size());
        RecordingScope scope = telemetry.scopes.getFirst();
        assertEquals("notifications.send", telemetry.observation.spanName());
        assertEquals("EMAIL", telemetry.observation.channel());
        assertFalse(telemetry.observation.async());
        assertFalse(telemetry.observation.batch());
        assertEquals(0, telemetry.observation.candidateCount());
        assertEquals("1", scope.attributes.get("notifications.candidate.count"));
        assertEquals("SendGrid", scope.attributes.get("notifications.selected.provider"));
        assertTrue(scope.events.contains("notification.provider.attempt"));
        assertTrue(scope.events.contains("notification.provider.success"));
        assertEquals(result.id(), scope.successResultId);
        assertTrue(scope.closed);
    }

    @Test
    void sendAsyncPropagatesContextIntoTheWorkerThread() {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            ContextTrackingTelemetry telemetry = new ContextTrackingTelemetry();
            telemetry.context.set("trace-parent");

            RecordingSender sender = new RecordingSender(NotificationChannel.SMS, "Twilio", telemetry.context);
            NotificationService service = new NotificationServiceBuilder()
                .executor(executor)
                .telemetry(telemetry)
                .register(sender)
                .build();

            NotificationResult result = service.sendAsync(NotificationRequest.sms(
                "+573001234567",
                "Body")).join();

            assertTrue(result.success());
            assertEquals(List.of("trace-parent"), sender.contextValues);
            assertEquals(List.of("trace-parent"), telemetry.contextValuesSeen);
        }
    }

    @Test
    void sendBatchAsyncPropagatesContextForEachRequest() {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            ContextTrackingTelemetry telemetry = new ContextTrackingTelemetry();
            telemetry.context.set("batch-parent");

            RecordingSender sender = new RecordingSender(NotificationChannel.PUSH, "Firebase", telemetry.context);
            NotificationService service = new NotificationServiceBuilder()
                .executor(executor)
                .telemetry(telemetry)
                .register(sender)
                .build();

            CompletableFuture<List<NotificationResult>> future = service.sendBatchAsync(List.of(
                NotificationRequest.push("push-token-12345", "Alert", "Body 1"),
                NotificationRequest.push("push-token-67890", "Alert", "Body 2")));

            List<NotificationResult> results = future.join();

            assertEquals(2, results.size());
            assertEquals(List.of("batch-parent", "batch-parent"), sender.contextValues);
            assertEquals(List.of("batch-parent", "batch-parent"), telemetry.contextValuesSeen);
        }
    }

    private static final class RecordingTelemetry implements NotificationTelemetryPort {
        private final List<RecordingScope> scopes = new ArrayList<>();
        private NotificationTelemetryObservation observation;

        @Override
        public NotificationTelemetryScope start(NotificationTelemetryObservation observation) {
            this.observation = observation;
            RecordingScope scope = new RecordingScope();
            scopes.add(scope);
            return scope;
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

    private static final class RecordingScope implements NotificationTelemetryScope {
        private final Map<String, String> attributes = new LinkedHashMap<>();
        private final List<String> events = new ArrayList<>();
        private String successResultId;
        private String failureErrorType;
        private String failureMessage;
        private boolean closed;

        @Override
        public void attribute(String key, String value) {
            attributes.put(key, value);
        }

        @Override
        public void event(String name, Map<String, String> attributes) {
            events.add(name);
        }

        @Override
        public void success(String resultId) {
            this.successResultId = resultId;
        }

        @Override
        public void failure(String errorType, String message) {
            this.failureErrorType = errorType;
            this.failureMessage = message;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class ContextTrackingTelemetry implements NotificationTelemetryPort {
        private final ThreadLocal<String> context = new ThreadLocal<>();
        private final List<String> contextValuesSeen = new ArrayList<>();

        @Override
        public NotificationTelemetryScope start(NotificationTelemetryObservation observation) {
            return new NotificationTelemetryScope() {
                @Override
                public void attribute(String key, String value) {
                }

                @Override
                public void event(String name, Map<String, String> attributes) {
                }

                @Override
                public void success(String resultId) {
                }

                @Override
                public void failure(String errorType, String message) {
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public <T> Supplier<T> contextualize(Supplier<T> task) {
            String captured = context.get();
            return () -> {
                String previous = context.get();
                context.set(captured);
                try {
                    return task.get();
                } finally {
                    contextValuesSeen.add(context.get());
                    if (previous == null) {
                        context.remove();
                    } else {
                        context.set(previous);
                    }
                }
            };
        }

        @Override
        public Runnable contextualize(Runnable task) {
            String captured = context.get();
            return () -> {
                String previous = context.get();
                context.set(captured);
                try {
                    task.run();
                } finally {
                    contextValuesSeen.add(context.get());
                    if (previous == null) {
                        context.remove();
                    } else {
                        context.set(previous);
                    }
                }
            };
        }
    }

    private static final class RecordingSender implements NotificationSender {
        private final NotificationChannel channel;
        private final String provider;
        private final ThreadLocal<String> context;
        private final List<String> contextValues;

        private RecordingSender(NotificationChannel channel, String provider) {
            this(channel, provider, null);
        }

        private RecordingSender(NotificationChannel channel, String provider, ThreadLocal<String> context) {
            this.channel = channel;
            this.provider = provider;
            this.context = context;
            this.contextValues = new ArrayList<>();
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
            if (context != null) {
                contextValues.add(context.get());
            }
            return NotificationResult.success(channel, provider);
        }
    }
}
