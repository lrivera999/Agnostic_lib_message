package com.demo.notifications.observability.otel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;

import com.demo.notifications.observability.NotificationTelemetryObservation;
import com.demo.notifications.observability.NotificationTelemetryScope;

class OpenTelemetryNotificationTelemetryAdapterTest {

    private final OpenTelemetryNotificationTelemetryAdapter adapter =
        new OpenTelemetryNotificationTelemetryAdapter(OpenTelemetry.noop());

    @Test
    void contextualizeSupplierRestoresCapturedContext() {
        ContextKey<String> tenantKey = ContextKey.named("tenant");

        Supplier<String> contextualized;
        try (Scope ignored = Context.current().with(tenantKey, "acme").makeCurrent()) {
            contextualized = adapter.contextualize(() -> Context.current().get(tenantKey));
        }

        assertEquals("acme", contextualized.get());
    }

    @Test
    void contextualizeRunnableRestoresCapturedContext() {
        ContextKey<String> requestKey = ContextKey.named("request-id");
        AtomicReference<String> observed = new AtomicReference<>();

        Runnable contextualized;
        try (Scope ignored = Context.current().with(requestKey, "req-123").makeCurrent()) {
            contextualized = adapter.contextualize(() -> observed.set(Context.current().get(requestKey)));
        }

        contextualized.run();

        assertEquals("req-123", observed.get());
    }

    @Test
    void startReturnsUsableScope() {
        NotificationTelemetryObservation observation = NotificationTelemetryObservation.of(
            "notifications.send",
            "email",
            "SendGrid",
            true,
            false,
            1,
            Map.of("notifications.message.id", "msg-123"));

        NotificationTelemetryScope scope = adapter.start(observation);

        assertNotNull(scope);

        scope.attribute("notifications.channel", "email");
        scope.event("notifications.started", Map.of("provider", "SendGrid"));
        scope.success("result-123");
        scope.failure("TimeoutException", "simulated timeout");
        scope.close();
        scope.close();
    }
}
