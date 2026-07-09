package com.demo.notifications.services;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.NotificationResult;
import com.demo.notifications.core.enums.MessageChannel;
import com.demo.notifications.core.enums.NotificationChannel;
import com.demo.notifications.core.exceptions.NotificationException;
import com.demo.notifications.core.interfaces.NotificationSender;
import com.demo.notifications.observability.NotificationTelemetryObservation;
import com.demo.notifications.observability.NotificationTelemetryPort;
import com.demo.notifications.observability.NotificationTelemetryScope;
import com.demo.notifications.validations.NotificationValidator;

public final class NotificationService {

    private final Map<NotificationChannel, Map<String, NotificationSender>> sendersByChannel;
    private final Map<NotificationChannel, String> activeProviders;
    private final Map<NotificationChannel, List<String>> fallbackProviders;
    private final Executor executor;
    private final NotificationTelemetryPort telemetry;

    NotificationService(
        Map<NotificationChannel, Map<String, NotificationSender>> sendersByChannel,
        Map<NotificationChannel, String> activeProviders,
        Map<NotificationChannel, List<String>> fallbackProviders,
        Executor executor,
        NotificationTelemetryPort telemetry) {
        this.sendersByChannel = sendersByChannel.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> Map.copyOf(new LinkedHashMap<>(entry.getValue()))));
        this.activeProviders = Map.copyOf(activeProviders);
        this.fallbackProviders = fallbackProviders.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue())));
        this.executor = executor;
        this.telemetry = telemetry;
    }

    public NotificationResult send(NotificationRequest request) {
        NotificationTelemetryScope scope = telemetry.start(observationFor(request, false, false, 0, null));
        try {
            NotificationValidator.validate(request);

            List<NotificationSender> candidates = resolveSenders(request.channel());
            scope.attribute("notifications.candidate.count", Integer.toString(candidates.size()));

            NotificationResult result = sendWithCandidates(request, candidates, scope);
            if (result.success()) {
                scope.attribute("notifications.selected.provider", result.provider());
                scope.success(result.id());
            } else {
                scope.failure("notification_send_failed", normalizeFailureMessage(result.message()));
            }
            return result;
        } catch (RuntimeException ex) {
            scope.failure(ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        } finally {
            scope.close();
        }
    }

    public CompletableFuture<NotificationResult> sendAsync(NotificationRequest request) {
        Supplier<NotificationResult> task = telemetry.contextualize(() -> send(request));
        return CompletableFuture.supplyAsync(task, executor);
    }

    public List<NotificationResult> sendBatch(Collection<NotificationRequest> requests) {
        return requests.stream()
                .map(this::send)
                .toList();
    }

    public CompletableFuture<List<NotificationResult>> sendBatchAsync(Collection<NotificationRequest> requests) {
        List<CompletableFuture<NotificationResult>> futures = requests.stream()
                .map(this::sendAsync)
                .toList();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    private List<NotificationSender> resolveSenders(NotificationChannel channel) {
        Map<String, NotificationSender> channelSenders = sendersByChannel.get(channel);
        if (channelSenders == null || channelSenders.isEmpty()) {
            throw new NotificationException(MessageChannel.MSG_CHANNEL_NOT_SUPPORTED + ": " + channel);
        }

        List<String> fallbackChain = fallbackProviders.get(channel);
        if (fallbackChain != null) {
            return fallbackChain.stream()
                .map(provider -> resolveSender(channelSenders, channel, provider))
                .toList();
        }

        if (channelSenders.size() == 1) {
            return List.of(channelSenders.values().iterator().next());
        }

        String activeProvider = activeProviders.get(channel);
        if (activeProvider == null || activeProvider.isBlank()) {
            throw new NotificationException(MessageChannel.MSG_PROVIDER_SELECTION_REQUIRED + channel);
        }

        return List.of(resolveSender(channelSenders, channel, activeProvider));
    }

    private NotificationResult sendWithCandidates(
        NotificationRequest request,
        List<NotificationSender> candidates,
        NotificationTelemetryScope scope) {
        List<String> failures = new ArrayList<>();
        int attempt = 0;

        for (NotificationSender sender : candidates) {
            attempt++;
            scope.event("notification.provider.attempt", Map.of(
                "provider", sender.provider(),
                "attempt", Integer.toString(attempt)));
            try {
                NotificationResult result = sender.send(request);
                if (result != null && result.success()) {
                    scope.event("notification.provider.success", Map.of(
                        "provider", sender.provider(),
                        "attempt", Integer.toString(attempt)));
                    return result;
                }

                String failureMessage = result == null ? "respuesta nula" : result.message();
                failures.add(formatFailure(sender.provider(), failureMessage));
                scope.event("notification.provider.failure", Map.of(
                    "provider", sender.provider(),
                    "attempt", Integer.toString(attempt),
                    "reason", normalizeFailureMessage(failureMessage)));
            } catch (RuntimeException ex) {
                failures.add(formatFailure(sender.provider(), ex.getMessage()));
                scope.event("notification.provider.exception", Map.of(
                    "provider", sender.provider(),
                    "attempt", Integer.toString(attempt),
                    "reason", normalizeFailureMessage(ex.getMessage())));
            }
        }

        return NotificationResult.error(
            request.channel(),
            candidates.isEmpty() ? "desconocido" : candidates.get(0).provider(),
            String.join(" | ", failures));
    }

    private NotificationSender resolveSender(
        Map<String, NotificationSender> channelSenders,
        NotificationChannel channel,
        String provider) {
        return channelSenders.entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(provider))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow(() -> new NotificationException(
                MessageChannel.MSG_PROVIDER_NOT_REGISTERED + provider + ": " + channel));
    }

    private static String formatFailure(String provider, String message) {
        return provider + " -> " + normalizeFailureMessage(message);
    }

    private static String normalizeFailureMessage(String message) {
        if (message == null || message.isBlank()) {
            return "sin detalle";
        }

        if (message.startsWith(MessageChannel.MSG_ERROR)) {
            return message.substring(MessageChannel.MSG_ERROR.length()).trim();
        }

        return message.trim();
    }

    private static NotificationTelemetryObservation observationFor(
        NotificationRequest request,
        boolean async,
        boolean batch,
        int candidateCount,
        String provider) {

        return NotificationTelemetryObservation.of(
            "notifications.send",
            request == null || request.channel() == null ? "unknown" : request.channel().name(),
            provider,
            async,
            batch,
            candidateCount,
            Map.of());
    }
}
