package com.demo.notifications.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.demo.notifications.core.enums.NotificationChannel;

public record NotificationRequest ( 
    NotificationChannel channel,
    String recipient,
    String message,
    Map<String, String> metadata) {

    private static final String SUBJECT_KEY = "subject";
    private static final String TITLE_KEY = "title";

    public NotificationRequest {
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(metadata));
    }

    public static NotificationRequest email(String to, String subject, String message) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put(SUBJECT_KEY, subject);
        return new NotificationRequest(NotificationChannel.EMAIL, to, message, metadata);
    }

    public static NotificationRequest sms(String phone, String message) {
        return new NotificationRequest(NotificationChannel.SMS, phone, message, Map.of());
    }

    public static NotificationRequest push(String deviceToken, String title, String message) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put(TITLE_KEY, title);
        return new NotificationRequest(NotificationChannel.PUSH, deviceToken, message, metadata);
    }

    public String subject() {
        return metadata.get(SUBJECT_KEY);
    }

    public String title() {
        return metadata.get(TITLE_KEY);
    }
}
