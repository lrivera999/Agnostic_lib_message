package com.demo.notifications.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.NotificationResult;
import com.demo.notifications.core.enums.MessageChannel;
import com.demo.notifications.core.enums.NotificationChannel;
import com.demo.notifications.core.exceptions.NotificationException;
import com.demo.notifications.core.interfaces.NotificationSender;

class NotificationServiceBuilderTest {

    @Test
    void registerNullSenderFailsFast() {
        NotificationException ex = assertThrows(NotificationException.class,
            () -> new NotificationServiceBuilder().register(null));

        assertEquals(MessageChannel.MSG_SENDER_NULL, ex.getMessage());
    }

    @Test
    void buildWithoutSendersFailsFast() {
        NotificationException ex = assertThrows(NotificationException.class,
            () -> new NotificationServiceBuilder().build());

        assertEquals(MessageChannel.MSG_SENDER_REGISTRED, ex.getMessage());
    }

    @Test
    void nullExecutorIsRejected() {
        NotificationException ex = assertThrows(NotificationException.class,
            () -> new NotificationServiceBuilder().executor(null));

        assertEquals(MessageChannel.MSG_EXECUTOR_REGISTRED, ex.getMessage());
    }

    @Test
    void nullTelemetryIsRejected() {
        NotificationException ex = assertThrows(NotificationException.class,
            () -> new NotificationServiceBuilder().telemetry(null));

        assertEquals("La telemetria proporcionada no puede ser nula \t", ex.getMessage());
    }

    @Test
    void buildCreatesServiceWhenSenderIsRegistered() {
        NotificationService service = new NotificationServiceBuilder()
            .executor(Runnable::run)
            .register(new StubSender())
            .build();

        assertNotNull(service);
    }

    @Test
    void buildAllowsMultipleProvidersPerChannelWhenActiveProviderIsDefined() {
        RecordingSender sendgrid = new RecordingSender(NotificationChannel.EMAIL, "SendGrid");
        RecordingSender mailgun = new RecordingSender(NotificationChannel.EMAIL, "Mailgun");

        NotificationService service = new NotificationServiceBuilder()
            .executor(Runnable::run)
            .register(sendgrid)
            .register(mailgun)
            .activeProvider(NotificationChannel.EMAIL, "Mailgun")
            .build();

        NotificationResult result = service.send(NotificationRequest.email(
            "user@example.com",
            "Bienvenido",
            "Hola"));

        assertTrue(sendgrid.lastRequest == null);
        assertNotNull(mailgun.lastRequest);
        assertEquals("Mailgun", result.provider());
    }

    @Test
    void buildFailsWhenMultipleProvidersDoNotHaveActiveSelection() {
        NotificationException ex = assertThrows(NotificationException.class,
            () -> new NotificationServiceBuilder()
                .executor(Runnable::run)
                .register(new StubSender())
                .register(new RecordingSender(NotificationChannel.EMAIL, "Mailgun"))
                .build());

        assertEquals(
            MessageChannel.MSG_PROVIDER_SELECTION_REQUIRED + NotificationChannel.EMAIL,
            ex.getMessage());
    }

    @Test
    void duplicateProviderPerChannelIsRejected() {
        NotificationException ex = assertThrows(NotificationException.class,
            () -> new NotificationServiceBuilder()
                .register(new RecordingSender(NotificationChannel.EMAIL, "SendGrid"))
                .register(new RecordingSender(NotificationChannel.EMAIL, "SendGrid")));

        assertEquals(
            MessageChannel.MSG_PROVIDER_ALREADY_REGISTERED + "SendGrid: " + NotificationChannel.EMAIL,
            ex.getMessage());
    }

    @Test
    void fallbackProvidersMustCoverTheRegisteredProvidersForTheChannel() {
        NotificationException ex = assertThrows(NotificationException.class,
            () -> new NotificationServiceBuilder()
                .register(new StubSender())
                .register(new RecordingSender(NotificationChannel.EMAIL, "Mailgun"))
                .fallbackProviders(NotificationChannel.EMAIL, "Mailgun")
                .build());

        assertEquals(
            MessageChannel.MSG_PROVIDER_FALLBACK_INCOMPLETE + NotificationChannel.EMAIL,
            ex.getMessage());
    }

    @Test
    void fallbackPolicyCannotBeCombinedWithAnExplicitActiveProviderForTheSameChannel() {
        NotificationException ex = assertThrows(NotificationException.class,
            () -> new NotificationServiceBuilder()
                .register(new StubSender())
                .register(new RecordingSender(NotificationChannel.EMAIL, "Mailgun"))
                .activeProvider(NotificationChannel.EMAIL, "Mailgun")
                .fallbackProviders(NotificationChannel.EMAIL, "Stub", "Mailgun")
                .build());

        assertEquals(
            MessageChannel.MSG_PROVIDER_POLICY_CONFLICT + NotificationChannel.EMAIL,
            ex.getMessage());
    }

    private static final class StubSender implements NotificationSender {
        @Override
        public NotificationChannel channel() {
            return NotificationChannel.EMAIL;
        }

        @Override
        public String provider() {
            return "Stub";
        }

        @Override
        public NotificationResult send(NotificationRequest request) {
            return NotificationResult.success(channel(), provider());
        }
    }

    private static final class RecordingSender implements NotificationSender {
        private final NotificationChannel channel;
        private final String provider;
        private NotificationRequest lastRequest;

        private RecordingSender(NotificationChannel channel, String provider) {
            this.channel = channel;
            this.provider = provider;
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
            return NotificationResult.success(channel, provider);
        }
    }
}
