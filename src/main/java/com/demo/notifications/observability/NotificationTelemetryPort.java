package com.demo.notifications.observability;

import java.util.function.Supplier;

public interface NotificationTelemetryPort {

    NotificationTelemetryScope start(NotificationTelemetryObservation observation);

    <T> Supplier<T> contextualize(Supplier<T> task);

    Runnable contextualize(Runnable task);

    static NotificationTelemetryPort noop() {
        return NoopNotificationTelemetryPort.INSTANCE;
    }
}
