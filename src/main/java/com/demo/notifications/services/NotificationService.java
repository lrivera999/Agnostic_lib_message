package com.demo.notifications.services;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.demo.notifications.core.NotificationRequest;
import com.demo.notifications.core.NotificationResult;
import com.demo.notifications.core.enums.MessageChannel;
import com.demo.notifications.core.enums.NotificationChannel;
import com.demo.notifications.core.exceptions.NotificationException;
import com.demo.notifications.core.interfaces.NotificationSender;
import com.demo.notifications.validations.NotificationValidator;

public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final Map<NotificationChannel, Map<String, NotificationSender>> sendersByChannel;
    private final Map<NotificationChannel, String> activeProviders;
    private final Map<NotificationChannel, List<String>> fallbackProviders;
    private final Executor executor;

    @SuppressWarnings("unused")
    NotificationService(
        Map<NotificationChannel, Map<String, NotificationSender>> sendersByChannel,
        Map<NotificationChannel, String> activeProviders,
        Map<NotificationChannel, List<String>> fallbackProviders,
        Executor executor) {
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
    }

    public NotificationResult send(NotificationRequest request) {
        NotificationValidator.validate(request);

        return sendWithCandidates(request, resolveSenders(request.channel()));
    }

    public CompletableFuture<NotificationResult> sendAsync(NotificationRequest request) {
        return CompletableFuture.supplyAsync(() -> send(request), executor);
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

    private NotificationResult sendWithCandidates(NotificationRequest request, List<NotificationSender> candidates) {
        List<String> failures = new ArrayList<>();

        for (NotificationSender sender : candidates) {
            try {
                NotificationResult result = sender.send(request);
                if (result != null && result.success()) {
                    return result;
                }

                failures.add(formatFailure(sender.provider(), result == null ? "respuesta nula" : result.message()));
            } catch (RuntimeException ex) {
                failures.add(formatFailure(sender.provider(), ex.getMessage()));
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
}
