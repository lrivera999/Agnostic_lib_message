package com.demo.notifications.observability;

import java.util.LinkedHashMap;
import java.util.Map;

public record NotificationTelemetryObservation(
    String spanName,
    String channel,
    String provider,
    boolean async,
    boolean batch,
    int candidateCount,
    Map<String, String> attributes) {

    public static final String DEFAULT_SPAN_NAME = "notifications.send";

    public NotificationTelemetryObservation {
        spanName = normalize(spanName, DEFAULT_SPAN_NAME);
        channel = normalize(channel, "unknown");
        provider = normalizeNullable(provider);
        attributes = sanitize(attributes);
    }

    public static NotificationTelemetryObservation of(
        String spanName,
        String channel,
        String provider,
        boolean async,
        boolean batch,
        int candidateCount,
        Map<String, String> attributes) {

        return new NotificationTelemetryObservation(
            spanName,
            channel,
            provider,
            async,
            batch,
            candidateCount,
            attributes);
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value.trim();
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static Map<String, String> sanitize(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        attributes.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                normalized.put(key.trim(), value);
            }
        });

        if (normalized.isEmpty()) {
            return Map.of();
        }

        return Map.copyOf(normalized);
    }
}
