package com.demo.notifications.observability;

import java.util.Map;

public interface NotificationTelemetryScope extends AutoCloseable {

    void attribute(String key, String value);

    void event(String name, Map<String, String> attributes);

    void success(String resultId);

    void failure(String errorType, String message);

    @Override
    void close();
}
