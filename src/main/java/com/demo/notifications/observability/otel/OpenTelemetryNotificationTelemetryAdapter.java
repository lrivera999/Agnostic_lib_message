package com.demo.notifications.observability.otel;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import com.demo.notifications.observability.NotificationTelemetryObservation;
import com.demo.notifications.observability.NotificationTelemetryPort;
import com.demo.notifications.observability.NotificationTelemetryScope;

public final class OpenTelemetryNotificationTelemetryAdapter implements NotificationTelemetryPort {

    private static final String INSTRUMENTATION_NAME = "com.demo.notifications";
    private static final String INSTRUMENTATION_VERSION = "1.0.0";

    private final Tracer tracer;

    public OpenTelemetryNotificationTelemetryAdapter(OpenTelemetry openTelemetry) {
        this.tracer = Objects.requireNonNull(openTelemetry, "openTelemetry")
            .getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
    }

    public static OpenTelemetryNotificationTelemetryAdapter usingGlobal() {
        return new OpenTelemetryNotificationTelemetryAdapter(GlobalOpenTelemetry.get());
    }

    @Override
    public NotificationTelemetryScope start(NotificationTelemetryObservation observation) {
        Objects.requireNonNull(observation, "observation");

        Span span = tracer.spanBuilder(observation.spanName())
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();

        if (observation.channel() != null) {
            span.setAttribute("notifications.channel", observation.channel());
        }
        if (observation.provider() != null) {
            span.setAttribute("notifications.provider", observation.provider());
        }
        span.setAttribute("notifications.async", observation.async());
        span.setAttribute("notifications.batch", observation.batch());
        span.setAttribute("notifications.candidate.count", observation.candidateCount());
        observation.attributes().forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                span.setAttribute(key, value);
            }
        });

        Scope scope = span.makeCurrent();
        return new OpenTelemetryNotificationTelemetryScope(span, scope);
    }

    @Override
    public <T> Supplier<T> contextualize(Supplier<T> task) {
        Objects.requireNonNull(task, "task");
        Context captured = Context.current();
        return () -> {
            try (Scope ignored = captured.makeCurrent()) {
                return task.get();
            }
        };
    }

    @Override
    public Runnable contextualize(Runnable task) {
        Objects.requireNonNull(task, "task");
        Context captured = Context.current();
        return () -> {
            try (Scope ignored = captured.makeCurrent()) {
                task.run();
            }
        };
    }

    private static final class OpenTelemetryNotificationTelemetryScope implements NotificationTelemetryScope {

        private final Span span;
        private final Scope scope;
        private boolean closed;

        private OpenTelemetryNotificationTelemetryScope(Span span, Scope scope) {
            this.span = span;
            this.scope = scope;
        }

        @Override
        public void attribute(String key, String value) {
            if (key != null && !key.isBlank() && value != null) {
                span.setAttribute(key, value);
            }
        }

        @Override
        public void event(String name, Map<String, String> attributes) {
            if (name == null || name.isBlank()) {
                return;
            }

            span.addEvent(name, toAttributes(attributes));
        }

        @Override
        public void success(String resultId) {
            if (resultId != null && !resultId.isBlank()) {
                span.setAttribute("notifications.result.id", resultId);
            }
            span.setStatus(StatusCode.OK);
        }

        @Override
        public void failure(String errorType, String message) {
            if (errorType != null && !errorType.isBlank()) {
                span.setAttribute("notifications.error.type", errorType);
            }
            if (message != null && !message.isBlank()) {
                span.setAttribute("notifications.error.message", message);
            }
            span.setStatus(StatusCode.ERROR, message == null || message.isBlank() ? "notification failure" : message);
            span.addEvent("notification.failure", toAttributes(Map.of(
                "error.type", errorType == null ? "unknown" : errorType,
                "error.message", message == null ? "sin detalle" : message)));
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }

            closed = true;
            scope.close();
            span.end();
        }

        private static Attributes toAttributes(Map<String, String> attributes) {
            if (attributes == null || attributes.isEmpty()) {
                return Attributes.empty();
            }

            AttributesBuilder builder = Attributes.builder();
            attributes.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    builder.put(AttributeKey.stringKey(key), value);
                }
            });

            return builder.build();
        }
    }
}
