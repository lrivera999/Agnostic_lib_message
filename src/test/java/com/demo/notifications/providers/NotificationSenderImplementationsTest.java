package com.demo.notifications.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.NotificationResult;
import com.demo.notifications.core.enums.NotificationChannel;
import com.demo.notifications.providers.email.impl.GridEmailSender;
import com.demo.notifications.providers.email.impl.MailgunEmailSender;
import com.demo.notifications.providers.push.impl.FirebasePushSender;
import com.demo.notifications.providers.sms.impl.TwilioSmsSender;

class NotificationSenderImplementationsTest {

    @Test
    void gridEmailSenderReturnsSuccessResult() {
        NotificationResult result = new GridEmailSender("api-key", "demo.example", "noreply@demo.example")
            .send(NotificationRequest.email("user@example.com", "Bienvenido", "Hola"));

        assertSenderResult(result, NotificationChannel.EMAIL, "SendGrid");
    }

    @Test
    void mailgunEmailSenderReturnsSuccessResult() {
        NotificationResult result = new MailgunEmailSender("api-key", "demo.example", "noreply@demo.example")
            .send(NotificationRequest.email("user@example.com", "Bienvenido", "Hola"));

        assertSenderResult(result, NotificationChannel.EMAIL, "Mailgun");
    }

    @Test
    void twilioSmsSenderReturnsSuccessResult() {
        NotificationResult result = new TwilioSmsSender("auth-token", "account-sid", "+15550000000")
            .send(NotificationRequest.sms("+573001234567", "Tu código es 123456"));

        assertSenderResult(result, NotificationChannel.SMS, "Twilio");
    }

    @Test
    void firebasePushSenderReturnsSuccessResult() {
        NotificationResult result = new FirebasePushSender("api-key", "project-id", "service-account.json")
            .send(NotificationRequest.push("device-token-12345", "Aviso", "Mensaje"));

        assertSenderResult(result, NotificationChannel.PUSH, "Firebase");
    }

    private static void assertSenderResult(NotificationResult result, NotificationChannel channel, String provider) {
        assertEquals(channel, result.channel());
        assertEquals(provider, result.provider());
        assertTrue(result.success());
        assertTrue(result.id() != null && !result.id().isBlank());
        assertTrue(result.sentAt() != null);
    }
}
