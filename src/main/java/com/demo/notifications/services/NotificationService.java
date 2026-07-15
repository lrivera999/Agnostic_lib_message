package com.demo.notifications.services;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
    private final Map<NotificationChannel, Map<String, NotificationResilienceRuntime>> resilienceRuntimes;
    private final Executor executor;
    private final NotificationTelemetryPort telemetry;

    NotificationService(
        Map<NotificationChannel, Map<String, NotificationSender>> sendersByChannel,
        Map<NotificationChannel, String> activeProviders,
        Map<NotificationChannel, List<String>> fallbackProviders,
        Map<NotificationChannel, Map<String, NotificationResiliencePolicy>> resiliencePolicies,
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
        this.resilienceRuntimes = resiliencePolicies.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> entry.getValue().entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        policyEntry -> new NotificationResilienceRuntime(policyEntry.getValue())))));
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

        for (int index = 0; index < candidates.size(); index++) {
            NotificationSender sender = candidates.get(index);
            NotificationResult result = sendWithResilience(request, sender, scope);
            if (result != null && result.success()) {
                return result;
            }

            String failureMessage = result == null ? "respuesta nula" : result.message();
            failures.add(formatFailure(sender.provider(), failureMessage));

            if (index + 1 < candidates.size()) {
                NotificationSender nextSender = candidates.get(index + 1);
                scope.event("notification.provider.failover", Map.of(
                    "from", sender.provider(),
                    "to", nextSender.provider(),
                    "reason", normalizeFailureMessage(failureMessage)));
            }
        }

        return NotificationResult.error(
            request.channel(),
            candidates.isEmpty() ? "desconocido" : candidates.get(0).provider(),
            String.join(" | ", failures));
    }

    private NotificationResult sendWithResilience(
        NotificationRequest request,
        NotificationSender sender,
        NotificationTelemetryScope scope) {
        NotificationResilienceRuntime runtime = resilienceRuntimeFor(request.channel(), sender.provider());
        if (!runtime.circuitBreaker.allowRequest()) {
            scope.event("notification.provider.circuit_open", Map.of(
                "provider", sender.provider()));
            return NotificationResult.error(
                request.channel(),
                sender.provider(),
                MessageChannel.MSG_PROVIDER_CIRCUIT_OPEN + sender.provider());
        }

        NotificationResiliencePolicy policy = runtime.policy;
        NotificationResiliencePolicy.RetryPolicy retry = policy.retry();
        int maxAttempts = retry.enabled() ? retry.maxAttempts() : 1;
        List<String> failures = new ArrayList<>();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (!runtime.rateLimiter.tryAcquire()) {
                scope.event("notification.provider.rate_limited", Map.of(
                    "provider", sender.provider(),
                    "attempt", Integer.toString(attempt)));
                return NotificationResult.error(
                    request.channel(),
                    sender.provider(),
                    MessageChannel.MSG_PROVIDER_RATE_LIMITED + sender.provider());
            }

            scope.event("notification.provider.attempt", Map.of(
                "provider", sender.provider(),
                "attempt", Integer.toString(attempt)));

            try {
                NotificationResult result = executeAttempt(sender, request, policy.timeout());
                if (result != null && result.success()) {
                    runtime.circuitBreaker.recordSuccess();
                    scope.event("notification.provider.success", Map.of(
                        "provider", sender.provider(),
                        "attempt", Integer.toString(attempt)));
                    return result;
                }

                String failureMessage = result == null ? "respuesta nula" : result.message();
                failures.add(formatFailure(sender.provider(), failureMessage));
                recordCircuitFailure(scope, runtime, sender, attempt);
                scope.event("notification.provider.failure", Map.of(
                    "provider", sender.provider(),
                    "attempt", Integer.toString(attempt),
                    "reason", normalizeFailureMessage(failureMessage)));

                if (attempt < maxAttempts && !runtime.circuitBreaker.isOpen()) {
                    Duration backoff = computeBackoff(retry, attempt);
                    if (!backoff.isZero()) {
                        scope.event("notification.provider.retry", Map.of(
                            "provider", sender.provider(),
                            "attempt", Integer.toString(attempt),
                            "backoff.ms", Long.toString(backoff.toMillis())));
                        sleep(backoff);
                    } else {
                        scope.event("notification.provider.retry", Map.of(
                            "provider", sender.provider(),
                            "attempt", Integer.toString(attempt)));
                    }
                    continue;
                }

                return NotificationResult.error(
                    request.channel(),
                    sender.provider(),
                    String.join(" | ", failures));
            } catch (RuntimeException ex) {
                String failureMessage = normalizeFailureMessage(ex.getMessage());
                failures.add(formatFailure(sender.provider(), failureMessage));
                recordCircuitFailure(scope, runtime, sender, attempt);
                scope.event("notification.provider.exception", Map.of(
                    "provider", sender.provider(),
                    "attempt", Integer.toString(attempt),
                    "reason", failureMessage));

                if (attempt < maxAttempts && !runtime.circuitBreaker.isOpen()) {
                    Duration backoff = computeBackoff(retry, attempt);
                    if (!backoff.isZero()) {
                        scope.event("notification.provider.retry", Map.of(
                            "provider", sender.provider(),
                            "attempt", Integer.toString(attempt),
                            "backoff.ms", Long.toString(backoff.toMillis())));
                        sleep(backoff);
                    } else {
                        scope.event("notification.provider.retry", Map.of(
                            "provider", sender.provider(),
                            "attempt", Integer.toString(attempt)));
                    }
                    continue;
                }

                return NotificationResult.error(
                    request.channel(),
                    sender.provider(),
                    String.join(" | ", failures));
            }
        }

        return NotificationResult.error(
            request.channel(),
            sender.provider(),
            String.join(" | ", failures));
    }

    private NotificationResult executeAttempt(
        NotificationSender sender,
        NotificationRequest request,
        Duration timeout) {
        Supplier<NotificationResult> task = () -> sender.send(request);
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return task.get();
        }

        AtomicReference<NotificationResult> resultRef = new AtomicReference<>();
        AtomicReference<RuntimeException> runtimeFailureRef = new AtomicReference<>();
        AtomicReference<Error> fatalFailureRef = new AtomicReference<>();
        Supplier<NotificationResult> contextualizedTask = telemetry.contextualize(task);
        Thread worker = Thread.ofVirtual().unstarted(() -> {
            try {
                resultRef.set(contextualizedTask.get());
            } catch (RuntimeException ex) {
                runtimeFailureRef.set(ex);
            } catch (Error error) {
                fatalFailureRef.set(error);
            }
        });

        worker.start();
        try {
            long timeoutNanos = timeout.toNanos();
            long timeoutMillis = TimeUnit.NANOSECONDS.toMillis(timeoutNanos);
            int timeoutRemainderNanos = (int) (timeoutNanos - TimeUnit.MILLISECONDS.toNanos(timeoutMillis));
            worker.join(timeoutMillis, timeoutRemainderNanos);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new NotificationException(MessageChannel.MSG_ERROR + "interrumpido", ex);
        }

        if (worker.isAlive()) {
            worker.interrupt();
            throw new NotificationException(MessageChannel.MSG_PROVIDER_TIMEOUT + sender.provider());
        }

        if (fatalFailureRef.get() != null) {
            throw fatalFailureRef.get();
        }
        if (runtimeFailureRef.get() != null) {
            throw runtimeFailureRef.get();
        }

        return resultRef.get();
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

    private NotificationResilienceRuntime resilienceRuntimeFor(NotificationChannel channel, String provider) {
        Map<String, NotificationResilienceRuntime> channelRuntimes = resilienceRuntimes.get(channel);
        if (channelRuntimes == null) {
            return NotificationResilienceRuntime.disabled();
        }

        NotificationResilienceRuntime runtime = channelRuntimes.get(normalizeProviderKey(provider));
        return runtime == null ? NotificationResilienceRuntime.disabled() : runtime;
    }

    private static Duration computeBackoff(NotificationResiliencePolicy.RetryPolicy retry, int attempt) {
        if (retry == null || !retry.enabled() || retry.maxAttempts() <= 1) {
            return Duration.ZERO;
        }

        long initialNanos = retry.initialBackoff().toNanos();
        if (initialNanos <= 0L) {
            return Duration.ZERO;
        }

        double multiplier = Math.pow(retry.backoffMultiplier(), Math.max(0, attempt - 1));
        long candidateNanos = (long) (initialNanos * multiplier);
        long maxNanos = retry.maxBackoff().toNanos();
        long normalizedNanos = maxNanos > 0L ? Math.min(candidateNanos, maxNanos) : candidateNanos;
        return Duration.ofNanos(Math.max(0L, normalizedNanos));
    }

    private static void sleep(Duration duration) {
        try {
            long nanos = duration.toNanos();
            long millis = TimeUnit.NANOSECONDS.toMillis(nanos);
            int remainderNanos = (int) (nanos - TimeUnit.MILLISECONDS.toNanos(millis));
            Thread.sleep(millis, remainderNanos);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new NotificationException(MessageChannel.MSG_ERROR + "interrumpido", ex);
        }
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

    private static void recordCircuitFailure(
        NotificationTelemetryScope scope,
        NotificationResilienceRuntime runtime,
        NotificationSender sender,
        int attempt) {

        if (runtime.circuitBreaker.recordFailure()) {
            scope.event("notification.provider.circuit_tripped", Map.of(
                "provider", sender.provider(),
                "attempt", Integer.toString(attempt)));
        }
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

    private static String normalizeProviderKey(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static final class NotificationResilienceRuntime {
        private static final NotificationResilienceRuntime DISABLED =
            new NotificationResilienceRuntime(NotificationResiliencePolicy.disabled());

        private final NotificationResiliencePolicy policy;
        private final ProviderCircuitBreaker circuitBreaker;
        private final ProviderRateLimiter rateLimiter;

        private NotificationResilienceRuntime(NotificationResiliencePolicy policy) {
            this.policy = policy == null ? NotificationResiliencePolicy.disabled() : policy;
            this.circuitBreaker = new ProviderCircuitBreaker(this.policy.circuitBreaker());
            this.rateLimiter = new ProviderRateLimiter(this.policy.rateLimit());
        }

        private static NotificationResilienceRuntime disabled() {
            return DISABLED;
        }
    }
}
