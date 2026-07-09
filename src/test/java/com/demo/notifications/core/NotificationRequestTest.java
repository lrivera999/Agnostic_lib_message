package com.demo.notifications.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.demo.notifications.core.enums.NotificationChannel;

class NotificationRequestTest {

    @Test
    void emailFactoryStoresSubjectInMetadata() {
        NotificationRequest request = NotificationRequest.email(
            "user@example.com",
            "Welcome",
            "Body");

        assertEquals(NotificationChannel.EMAIL, request.channel());
        assertEquals("user@example.com", request.recipient());
        assertEquals("Body", request.message());
        assertEquals("Welcome", request.subject());
        assertNull(request.title());
        assertEquals(Map.of("subject", "Welcome"), request.metadata());
    }

    @Test
    void pushFactoryStoresTitleInMetadata() {
        NotificationRequest request = NotificationRequest.push(
            "push-token-123456",
            "Alert",
            "Body");

        assertEquals(NotificationChannel.PUSH, request.channel());
        assertEquals("push-token-123456", request.recipient());
        assertEquals("Body", request.message());
        assertEquals("Alert", request.title());
        assertNull(request.subject());
        assertEquals(Map.of("title", "Alert"), request.metadata());
    }

    @Test
    void constructorCreatesDefensiveCopyOfMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key", "value");

        NotificationRequest request = new NotificationRequest(
            NotificationChannel.SMS,
            "+573001234567",
            "Body",
            metadata);

        metadata.put("key", "changed");

        assertEquals("value", request.metadata().get("key"));
        assertThrows(UnsupportedOperationException.class, () -> request.metadata().put("other", "value"));
    }
}
