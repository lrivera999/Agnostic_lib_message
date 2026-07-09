package com.demo.notifications.observability;

import java.util.Map;
import java.util.function.Supplier;

final class NoopNotificationTelemetryPort implements NotificationTelemetryPort {

    static final NoopNotificationTelemetryPort INSTANCE = new NoopNotificationTelemetryPort();

    private static final NotificationTelemetryScope NOOP_SCOPE = new NotificationTelemetryScope() {
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

    private NoopNotificationTelemetryPort() {
    }

    @Override
    public NotificationTelemetryScope start(NotificationTelemetryObservation observation) {
        return NOOP_SCOPE;
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
