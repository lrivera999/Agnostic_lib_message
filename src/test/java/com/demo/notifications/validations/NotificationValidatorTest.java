package com.demo.notifications.validations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.enums.MessageChannel;
import com.demo.notifications.core.enums.NotificationChannel;
import com.demo.notifications.core.exceptions.NotificationException;

class NotificationValidatorTest {

    @Test
    void validEmailRequestPassesValidation() {
        assertDoesNotThrow(() -> NotificationValidator.validate(
            NotificationRequest.email(
                "user@example.com",
                "Welcome",
                "Hello")));
    }

    @Test
    void invalidEmailIsRejected() {
        NotificationRequest request = NotificationRequest.email(
            "invalid-email",
            "Welcome",
            "Hello");

        NotificationException ex = assertThrows(NotificationException.class,
            () -> NotificationValidator.validate(request));

        assertEquals(MessageChannel.MSG_INVALID_EMAIL + "invalid-email", ex.getMessage());
    }

    @Test
    void blankEmailSubjectIsRejected() {
        NotificationRequest request = NotificationRequest.email(
            "user@example.com",
            "",
            "Hello");

        NotificationException ex = assertThrows(NotificationException.class,
            () -> NotificationValidator.validate(request));

        assertEquals(MessageChannel.MSG_BLANK_SUBJECT, ex.getMessage());
    }

    @Test
    void invalidPhoneIsRejected() {
        NotificationRequest request = NotificationRequest.sms(
            "12345",
            "Hello");

        NotificationException ex = assertThrows(NotificationException.class,
            () -> NotificationValidator.validate(request));

        assertEquals(MessageChannel.MSG_INVALID_PHONE + "12345", ex.getMessage());
    }

    @Test
    void validSmsRequestPassesValidation() {
        assertDoesNotThrow(() -> NotificationValidator.validate(
            NotificationRequest.sms(
                "+573001234567",
                "Hello")));
    }

    @Test
    void shortPushTokenIsRejected() {
        NotificationRequest request = new NotificationRequest(
            NotificationChannel.PUSH,
            "short",
            "Hello",
            Map.of("title", "Alert"));

        NotificationException ex = assertThrows(NotificationException.class,
            () -> NotificationValidator.validate(request));

        assertEquals(MessageChannel.MSG_INVALID_PUSH_TOKEN, ex.getMessage());
    }

    @Test
    void blankPushTitleIsRejected() {
        NotificationRequest request = NotificationRequest.push(
            "push-token-123456",
            "",
            "Hello");

        NotificationException ex = assertThrows(NotificationException.class,
            () -> NotificationValidator.validate(request));

        assertEquals(MessageChannel.MSG_BLANK_TITLE, ex.getMessage());
    }
}
