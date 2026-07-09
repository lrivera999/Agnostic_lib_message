package com.demo.notifications.core;

import java.time.Instant;
import java.util.UUID;

import com.demo.notifications.core.enums.MessageChannel;
import com.demo.notifications.core.enums.NotificationChannel;

public record NotificationResult(
    String id, 
    NotificationChannel channel,
    boolean success,
    String provider,
    String message,
    Instant sentAt) {
        public static NotificationResult success(
            NotificationChannel channel, 
            String provider) {
            return new NotificationResult(
                UUID.randomUUID().toString(), 
                channel, 
                true, 
                provider, 
                MessageChannel.MSG_SUCCESS, 
                Instant.now());
        }

        public static NotificationResult error(
            NotificationChannel channel, 
            String provider,
            String errorMessage) {
            return new NotificationResult(
                UUID.randomUUID().toString(), 
                channel, 
                false, 
                provider, 
                MessageChannel.MSG_ERROR + "\n" + errorMessage,
                Instant.now());
        }

}
