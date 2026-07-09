package com.demo.notifications.core;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.demo.notifications.core.enums.MessageChannel;
import com.demo.notifications.core.enums.NotificationChannel;

class NotificationResultTest {

    @Test
    void successFactoryCreatesSuccessfulResult() {
        NotificationResult result = NotificationResult.success(NotificationChannel.EMAIL, "SendGrid");

        assertAll(
            () -> assertNotNull(result.id()),
            () -> assertEquals(NotificationChannel.EMAIL, result.channel()),
            () -> assertTrue(result.success()),
            () -> assertEquals("SendGrid", result.provider()),
            () -> assertEquals(MessageChannel.MSG_SUCCESS, result.message()),
            () -> assertNotNull(result.sentAt()));
    }

    @Test
    void errorFactoryCreatesFailureResult() {
        NotificationResult result = NotificationResult.error(
            NotificationChannel.SMS,
            "Twilio",
            "timeout");

        assertAll(
            () -> assertNotNull(result.id()),
            () -> assertEquals(NotificationChannel.SMS, result.channel()),
            () -> assertFalse(result.success()),
            () -> assertEquals("Twilio", result.provider()),
            () -> assertTrue(result.message().startsWith(MessageChannel.MSG_ERROR)),
            () -> assertTrue(result.message().contains("timeout")),
            () -> assertNotNull(result.sentAt()));
    }
}
