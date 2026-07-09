package com.demo.notifications.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.NotificationResult;
import com.demo.notifications.core.enums.MessageChannel;
import com.demo.notifications.core.enums.NotificationChannel;
import com.demo.notifications.core.exceptions.NotificationException;
import com.demo.notifications.core.interfaces.NotificationSender;

class NotificationServiceTest {

    @Test
    void sendDelegatesToTheRegisteredSender() {
        RecordingSender sender = new RecordingSender(NotificationChannel.EMAIL, "StubEmail");
        NotificationService service = serviceWith(sender);
        NotificationRequest request = NotificationRequest.email(
            "user@example.com",
            "Welcome",
            "Body");

        NotificationResult result = service.send(request);

        assertSame(request, sender.lastRequest);
        assertEquals(NotificationChannel.EMAIL, result.channel());
        assertTrue(result.success());
        assertEquals("StubEmail", result.provider());
    }

    @Test
    void sendConvertsSenderFailuresIntoErrorResults() {
        RecordingSender sender = new RecordingSender(
            NotificationChannel.SMS,
            "StubSms",
            new NotificationException("boom"));
        NotificationService service = serviceWith(sender);
        NotificationRequest request = NotificationRequest.sms(
            "+573001234567",
            "Body");

        NotificationResult result = service.send(request);

        assertSame(request, sender.lastRequest);
        assertFalse(result.success());
        assertEquals(NotificationChannel.SMS, result.channel());
        assertEquals("StubSms", result.provider());
        assertTrue(result.message().startsWith(MessageChannel.MSG_ERROR));
        assertTrue(result.message().contains("boom"));
    }

    @Test
    void sendFallsBackToTheNextSenderWhenPrimaryFails() {
        RecordingSender primary = new RecordingSender(
            NotificationChannel.EMAIL,
            "SendGrid",
            new NotificationException("primary down"));
        RecordingSender backup = new RecordingSender(NotificationChannel.EMAIL, "Mailgun");

        NotificationService service = new NotificationServiceBuilder()
            .executor(Runnable::run)
            .register(primary)
            .register(backup)
            .fallbackProviders(NotificationChannel.EMAIL, "SendGrid", "Mailgun")
            .build();

        NotificationRequest request = NotificationRequest.email(
            "user@example.com",
            "Welcome",
            "Body");

        NotificationResult result = service.send(request);

        assertSame(request, primary.lastRequest);
        assertSame(request, backup.lastRequest);
        assertEquals(NotificationChannel.EMAIL, result.channel());
        assertTrue(result.success());
        assertEquals("Mailgun", result.provider());
    }

    @Test
    void sendFallsBackWhenPrimaryReturnsAnErrorResult() {
        RecordingSender primary = new RecordingSender(
            NotificationChannel.EMAIL,
            "SendGrid",
            NotificationResult.error(NotificationChannel.EMAIL, "SendGrid", "primary rejected"));
        RecordingSender backup = new RecordingSender(NotificationChannel.EMAIL, "Mailgun");

        NotificationService service = new NotificationServiceBuilder()
            .executor(Runnable::run)
            .register(primary)
            .register(backup)
            .fallbackProviders(NotificationChannel.EMAIL, "SendGrid", "Mailgun")
            .build();

        NotificationRequest request = NotificationRequest.email(
            "user@example.com",
            "Welcome",
            "Body");

        NotificationResult result = service.send(request);

        assertSame(request, primary.lastRequest);
        assertSame(request, backup.lastRequest);
        assertTrue(result.success());
        assertEquals("Mailgun", result.provider());
    }

    @Test
    void sendReturnsAnErrorWhenAllFallbackProvidersFail() {
        RecordingSender primary = new RecordingSender(
            NotificationChannel.EMAIL,
            "SendGrid",
            new NotificationException("primary down"));
        RecordingSender backup = new RecordingSender(
            NotificationChannel.EMAIL,
            "Mailgun",
            new NotificationException("backup down"));

        NotificationService service = new NotificationServiceBuilder()
            .executor(Runnable::run)
            .register(primary)
            .register(backup)
            .fallbackProviders(NotificationChannel.EMAIL, "SendGrid", "Mailgun")
            .build();

        NotificationRequest request = NotificationRequest.email(
            "user@example.com",
            "Welcome",
            "Body");

        NotificationResult result = service.send(request);

        assertSame(request, primary.lastRequest);
        assertSame(request, backup.lastRequest);
        assertFalse(result.success());
        assertEquals(NotificationChannel.EMAIL, result.channel());
        assertEquals("SendGrid", result.provider());
        assertTrue(result.message().contains("primary down"));
        assertTrue(result.message().contains("backup down"));
    }

    @Test
    void sendAsyncReturnsTheSameResult() {
        RecordingSender sender = new RecordingSender(NotificationChannel.PUSH, "StubPush");
        NotificationService service = serviceWith(sender);
        NotificationRequest request = NotificationRequest.push(
            "push-token-123456",
            "Alert",
            "Body");

        NotificationResult result = service.sendAsync(request).join();

        assertSame(request, sender.lastRequest);
        assertEquals(NotificationChannel.PUSH, result.channel());
        assertTrue(result.success());
    }

    @Test
    void sendBatchProcessesAllRequestsInOrder() {
        RecordingSender sender = new RecordingSender(NotificationChannel.EMAIL, "StubEmail");
        NotificationService service = serviceWith(sender);
        List<NotificationRequest> requests = List.of(
            NotificationRequest.email("one@example.com", "One", "Body one"),
            NotificationRequest.email("two@example.com", "Two", "Body two"));

        List<NotificationResult> results = service.sendBatch(requests);

        assertEquals(2, results.size());
        assertEquals("StubEmail", results.get(0).provider());
        assertEquals("StubEmail", results.get(1).provider());
    }

    @Test
    void sendBatchAsyncProcessesAllRequestsInOrder() {
        RecordingSender sender = new RecordingSender(NotificationChannel.EMAIL, "StubEmail");
        NotificationService service = serviceWith(sender);
        List<NotificationRequest> requests = List.of(
            NotificationRequest.email("one@example.com", "One", "Body one"),
            NotificationRequest.email("two@example.com", "Two", "Body two"));

        List<NotificationResult> results = service.sendBatchAsync(requests).join();

        assertEquals(2, results.size());
        assertEquals("StubEmail", results.get(0).provider());
        assertEquals("StubEmail", results.get(1).provider());
    }

    private static NotificationService serviceWith(NotificationSender sender) {
        return new NotificationServiceBuilder()
            .executor(Runnable::run)
            .register(sender)
            .build();
    }

    private static final class RecordingSender implements NotificationSender {
        private final NotificationChannel channel;
        private final String provider;
        private final NotificationException failure;
        private final NotificationResult response;
        private NotificationRequest lastRequest;

        private RecordingSender(NotificationChannel channel, String provider) {
            this(channel, provider, null, null);
        }

        private RecordingSender(NotificationChannel channel, String provider, NotificationException failure) {
            this(channel, provider, failure, null);
        }

        private RecordingSender(NotificationChannel channel, String provider, NotificationResult response) {
            this(channel, provider, null, response);
        }

        private RecordingSender(
            NotificationChannel channel,
            String provider,
            NotificationException failure,
            NotificationResult response) {
            this.channel = channel;
            this.provider = provider;
            this.failure = failure;
            this.response = response;
        }

        @Override
        public NotificationChannel channel() {
            return channel;
        }

        @Override
        public String provider() {
            return provider;
        }

        @Override
        public NotificationResult send(NotificationRequest request) {
            lastRequest = request;
            if (response != null) {
                return response;
            }
            if (failure != null) {
                throw failure;
            }
            return NotificationResult.success(channel, provider);
        }
    }
}
