package com.demo.notifications.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.NotificationResult;
import com.demo.notifications.core.enums.NotificationChannel;
import com.demo.notifications.core.exceptions.NotificationException;
import com.demo.notifications.core.interfaces.NotificationSender;

class NotificationServiceResilienceTest {

    @Test
    void retryRetriesTheSameProviderBeforeFailover() {
        SequencedSender primary = new SequencedSender(
            NotificationChannel.EMAIL,
            "SendGrid",
            List.of(
                Step.failure(new NotificationException("transient-1")),
                Step.failure(new NotificationException("transient-2")),
                Step.success()));
        SequencedSender backup = new SequencedSender(NotificationChannel.EMAIL, "Gmail", List.of(Step.success()));

        NotificationService service = new NotificationServiceBuilder()
            .executor(Runnable::run)
            .register(primary)
            .register(backup)
            .resilience(
                NotificationChannel.EMAIL,
                "SendGrid",
                NotificationResiliencePolicy.builder()
                    .retry(3, Duration.ZERO, 1.0d, Duration.ZERO)
                    .circuitBreaker(10, Duration.ofSeconds(5), 1)
                    .rateLimit(10, Duration.ofSeconds(1))
                    .build())
            .fallbackProviders(NotificationChannel.EMAIL, "SendGrid", "Gmail")
            .build();

        NotificationResult result = service.send(NotificationRequest.email(
            "user@example.com",
            "Welcome",
            "Body"));

        assertTrue(result.success());
        assertEquals(3, primary.attempts);
        assertEquals(0, backup.attempts);
    }

    @Test
    void circuitBreakerOpensAndSkipsPrimaryOnLaterRequests() {
        SequencedSender primary = new SequencedSender(
            NotificationChannel.EMAIL,
            "SendGrid",
            List.of(
                Step.failure(new NotificationException("primary-down")),
                Step.failure(new NotificationException("primary-down-again"))));
        SequencedSender backup = new SequencedSender(NotificationChannel.EMAIL, "Gmail", List.of(Step.success(), Step.success()));

        NotificationService service = new NotificationServiceBuilder()
            .executor(Runnable::run)
            .register(primary)
            .register(backup)
            .resilience(
                NotificationChannel.EMAIL,
                "SendGrid",
                NotificationResiliencePolicy.builder()
                    .circuitBreaker(1, Duration.ofMinutes(5), 1)
                    .rateLimit(10, Duration.ofSeconds(1))
                    .build())
            .fallbackProviders(NotificationChannel.EMAIL, "SendGrid", "Gmail")
            .build();

        NotificationResult first = service.send(NotificationRequest.email(
            "user@example.com",
            "Welcome",
            "Body"));
        NotificationResult second = service.send(NotificationRequest.email(
            "user@example.com",
            "Welcome again",
            "Body"));

        assertTrue(first.success());
        assertTrue(second.success());
        assertEquals(1, primary.attempts);
        assertEquals(2, backup.attempts);
    }

    @Test
    void rateLimitSkipsPrimaryAndFallsBackToBackup() {
        SequencedSender primary = new SequencedSender(
            NotificationChannel.EMAIL,
            "SendGrid",
            List.of(Step.success(), Step.success()));
        SequencedSender backup = new SequencedSender(NotificationChannel.EMAIL, "Gmail", List.of(Step.success(), Step.success()));

        NotificationService service = new NotificationServiceBuilder()
            .executor(Runnable::run)
            .register(primary)
            .register(backup)
            .resilience(
                NotificationChannel.EMAIL,
                "SendGrid",
                NotificationResiliencePolicy.builder()
                    .rateLimit(1, Duration.ofMinutes(5))
                    .build())
            .fallbackProviders(NotificationChannel.EMAIL, "SendGrid", "Gmail")
            .build();

        NotificationResult first = service.send(NotificationRequest.email(
            "user@example.com",
            "Welcome",
            "Body"));
        NotificationResult second = service.send(NotificationRequest.email(
            "user@example.com",
            "Welcome again",
            "Body"));

        assertTrue(first.success());
        assertTrue(second.success());
        assertEquals(1, primary.attempts);
        assertEquals(1, backup.attempts);
        assertEquals("Gmail", second.provider());
    }

    private static final class SequencedSender implements NotificationSender {
        private final NotificationChannel channel;
        private final String provider;
        private final List<Step> steps;
        private int attempts;
        private NotificationRequest lastRequest;

        private SequencedSender(NotificationChannel channel, String provider, List<Step> steps) {
            this.channel = channel;
            this.provider = provider;
            this.steps = steps;
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
            attempts++;

            Step step = attempts <= steps.size() ? steps.get(attempts - 1) : Step.success();
            if (step.failure() != null) {
                throw step.failure();
            }
            if (step.response() != null) {
                return step.response();
            }
            return NotificationResult.success(channel, provider);
        }
    }

    private record Step(NotificationResult response, NotificationException failure) {
        private static Step success() {
            return new Step(null, null);
        }

        private static Step failure(NotificationException failure) {
            return new Step(null, failure);
        }
    }
}
