package com.demo.notifications.services;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.demo.notifications.core.enums.MessageChannel;
import com.demo.notifications.core.enums.NotificationChannel;
import com.demo.notifications.core.exceptions.NotificationException;
import com.demo.notifications.core.interfaces.NotificationSender;

public class NotificationServiceBuilder {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceBuilder.class);

    private final Map<NotificationChannel, LinkedHashMap<String, NotificationSender>> senders = new EnumMap<>(NotificationChannel.class);
    private final Map<NotificationChannel, String> activeProviders = new EnumMap<>(NotificationChannel.class);
    private final Map<NotificationChannel, List<String>> fallbackProviders = new EnumMap<>(NotificationChannel.class);

    private Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    public NotificationServiceBuilder register(NotificationSender sender) {
        if (sender == null) {
            throw new NotificationException(MessageChannel.MSG_SENDER_NULL);
        }
        if (sender.channel() == null) {
            throw new NotificationException(MessageChannel.MSG_CHANNEL_NOT_BLANK);
        }
        if (sender.provider() == null || sender.provider().isBlank()) {
            throw new NotificationException(MessageChannel.MSG_PROVIDER_NOT_BLANK);
        }

        LinkedHashMap<String, NotificationSender> channelSenders = senders.computeIfAbsent(
            sender.channel(),
            ignored -> new LinkedHashMap<>());

        String normalizedProvider = normalizeProviderName(sender.provider());
        if (containsProvider(channelSenders, normalizedProvider)) {
            throw new NotificationException(
                MessageChannel.MSG_PROVIDER_ALREADY_REGISTERED + sender.provider() + ": " + sender.channel());
        }

        channelSenders.put(normalizedProvider, sender);
        return this;
    }

    public NotificationServiceBuilder activeProvider(NotificationChannel channel, String provider) {
        if (channel == null) {
            throw new NotificationException(MessageChannel.MSG_CHANNEL_NOT_BLANK);
        }
        if (provider == null || provider.isBlank()) {
            throw new NotificationException(MessageChannel.MSG_PROVIDER_NOT_BLANK);
        }
        activeProviders.put(channel, provider.trim());
        return this;
    }

    public NotificationServiceBuilder fallbackProviders(NotificationChannel channel, String... providers) {
        if (channel == null) {
            throw new NotificationException(MessageChannel.MSG_CHANNEL_NOT_BLANK);
        }
        if (providers == null || providers.length == 0) {
            throw new NotificationException(MessageChannel.MSG_PROVIDER_FALLBACK_REQUIRED);
        }

        List<String> normalizedProviders = new ArrayList<>(providers.length);
        for (String provider : providers) {
            if (provider == null || provider.isBlank()) {
                throw new NotificationException(MessageChannel.MSG_PROVIDER_NOT_BLANK);
            }

            String normalizedProvider = provider.trim();
            if (containsProvider(normalizedProviders, normalizedProvider)) {
                throw new NotificationException(
                    MessageChannel.MSG_PROVIDER_FALLBACK_DUPLICATED + channel);
            }

            normalizedProviders.add(normalizedProvider);
        }

        fallbackProviders.put(channel, List.copyOf(normalizedProviders));
        return this;
    }

    public NotificationServiceBuilder executor(Executor executor) {
        if (executor == null) {
            throw new NotificationException(MessageChannel.MSG_EXECUTOR_REGISTRED);
        }
        this.executor = executor;
        return this;
    }

    public NotificationService build() {
        if (senders.isEmpty()) {
            throw new NotificationException(MessageChannel.MSG_SENDER_REGISTRED);
        }

        validateProviderPolicies();

        Map<NotificationChannel, Map<String, NotificationSender>> immutableSenders = new EnumMap<>(NotificationChannel.class);
        for (Map.Entry<NotificationChannel, LinkedHashMap<String, NotificationSender>> entry : senders.entrySet()) {
            immutableSenders.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }

        return new NotificationService(
            immutableSenders,
            new HashMap<>(activeProviders),
            new HashMap<>(fallbackProviders),
            executor);
    }

    private void validateProviderPolicies() {
        for (Map.Entry<NotificationChannel, LinkedHashMap<String, NotificationSender>> entry : senders.entrySet()) {
            NotificationChannel channel = entry.getKey();
            LinkedHashMap<String, NotificationSender> channelSenders = entry.getValue();
            String activeProvider = activeProviders.get(channel);
            List<String> fallbackChain = fallbackProviders.get(channel);

            if (fallbackChain != null) {
                if (activeProvider != null) {
                    throw new NotificationException(
                        MessageChannel.MSG_PROVIDER_POLICY_CONFLICT + channel);
                }

                validateFallbackChain(channel, channelSenders, fallbackChain);
                continue;
            }

            if (channelSenders.size() == 1) {
                if (activeProvider != null && !containsProvider(channelSenders, activeProvider)) {
                    throw new NotificationException(
                        MessageChannel.MSG_PROVIDER_NOT_REGISTERED + activeProvider + ": " + channel);
                }
                continue;
            }

            if (activeProvider == null || activeProvider.isBlank()) {
                throw new NotificationException(MessageChannel.MSG_PROVIDER_SELECTION_REQUIRED + channel);
            }

            if (!containsProvider(channelSenders, activeProvider)) {
                throw new NotificationException(
                    MessageChannel.MSG_PROVIDER_NOT_REGISTERED + activeProvider + ": " + channel);
            }
        }

        for (Map.Entry<NotificationChannel, String> entry : activeProviders.entrySet()) {
            if (!senders.containsKey(entry.getKey())) {
                throw new NotificationException(
                    MessageChannel.MSG_PROVIDER_NOT_REGISTERED + entry.getValue() + ": " + entry.getKey());
            }
        }

        for (Map.Entry<NotificationChannel, List<String>> entry : fallbackProviders.entrySet()) {
            if (!senders.containsKey(entry.getKey())) {
                throw new NotificationException(
                    MessageChannel.MSG_PROVIDER_NOT_REGISTERED + entry.getValue().get(0) + ": " + entry.getKey());
            }
        }
    }

    private void validateFallbackChain(
        NotificationChannel channel,
        LinkedHashMap<String, NotificationSender> channelSenders,
        List<String> fallbackChain) {

        if (fallbackChain.size() != channelSenders.size()) {
            throw new NotificationException(
                MessageChannel.MSG_PROVIDER_FALLBACK_INCOMPLETE + channel);
        }

        for (String fallbackProvider : fallbackChain) {
            if (!containsProvider(channelSenders, fallbackProvider)) {
                throw new NotificationException(
                    MessageChannel.MSG_PROVIDER_NOT_REGISTERED + fallbackProvider + ": " + channel);
            }
        }
    }

    private static boolean containsProvider(Map<String, NotificationSender> channelSenders, String provider) {
        return channelSenders.keySet().stream()
            .anyMatch(existingProvider -> existingProvider.equalsIgnoreCase(provider));
    }

    private static boolean containsProvider(List<String> providers, String provider) {
        return providers.stream()
            .anyMatch(existingProvider -> existingProvider.equalsIgnoreCase(provider));
    }

    private static String normalizeProviderName(String provider) {
        return Objects.requireNonNull(provider).trim();
    }
}
