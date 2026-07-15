package com.demo.notifications.services;

import java.time.Duration;
import java.util.Objects;

public record NotificationResiliencePolicy(
    RetryPolicy retry,
    CircuitBreakerPolicy circuitBreaker,
    RateLimitPolicy rateLimit,
    Duration timeout) {

    public NotificationResiliencePolicy {
        retry = retry == null ? RetryPolicy.disabled() : retry;
        circuitBreaker = circuitBreaker == null ? CircuitBreakerPolicy.disabled() : circuitBreaker;
        rateLimit = rateLimit == null ? RateLimitPolicy.disabled() : rateLimit;
        timeout = timeout == null ? Duration.ZERO : timeout;

        if (timeout.isNegative()) {
            throw new IllegalArgumentException("El timeout no puede ser negativo");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static NotificationResiliencePolicy disabled() {
        return builder().build();
    }

    public boolean hasTimeout() {
        return !timeout.isZero() && !timeout.isNegative();
    }

    public static final class Builder {
        private RetryPolicy retry = RetryPolicy.disabled();
        private CircuitBreakerPolicy circuitBreaker = CircuitBreakerPolicy.disabled();
        private RateLimitPolicy rateLimit = RateLimitPolicy.disabled();
        private Duration timeout = Duration.ZERO;

        public Builder retry(int maxAttempts, Duration initialBackoff, double backoffMultiplier, Duration maxBackoff) {
            this.retry = new RetryPolicy(true, maxAttempts, initialBackoff, backoffMultiplier, maxBackoff);
            return this;
        }

        public Builder circuitBreaker(int failureThreshold, Duration openDuration, int halfOpenPermits) {
            this.circuitBreaker = new CircuitBreakerPolicy(true, failureThreshold, openDuration, halfOpenPermits);
            return this;
        }

        public Builder rateLimit(int permits, Duration window) {
            this.rateLimit = new RateLimitPolicy(true, permits, window);
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            if (timeout.isNegative()) {
                throw new IllegalArgumentException("El timeout no puede ser negativo");
            }
            return this;
        }

        public NotificationResiliencePolicy build() {
            return new NotificationResiliencePolicy(retry, circuitBreaker, rateLimit, timeout);
        }
    }

    public record RetryPolicy(
        boolean enabled,
        int maxAttempts,
        Duration initialBackoff,
        double backoffMultiplier,
        Duration maxBackoff) {

        public RetryPolicy {
            if (!enabled) {
                maxAttempts = 1;
                initialBackoff = Duration.ZERO;
                backoffMultiplier = 1.0d;
                maxBackoff = Duration.ZERO;
            } else {
                if (maxAttempts < 1) {
                    throw new IllegalArgumentException("La politica de retry debe permitir al menos un intento");
                }
                initialBackoff = Objects.requireNonNull(initialBackoff, "initialBackoff");
                maxBackoff = Objects.requireNonNull(maxBackoff, "maxBackoff");
                if (initialBackoff.isNegative()) {
                    throw new IllegalArgumentException("El backoff inicial no puede ser negativo");
                }
                if (maxBackoff.isNegative()) {
                    throw new IllegalArgumentException("El backoff maximo no puede ser negativo");
                }
                if (backoffMultiplier <= 0.0d) {
                    throw new IllegalArgumentException("El multiplicador de backoff debe ser mayor que cero");
                }
            }
        }

        public static RetryPolicy disabled() {
            return new RetryPolicy(false, 1, Duration.ZERO, 1.0d, Duration.ZERO);
        }
    }

    public record CircuitBreakerPolicy(
        boolean enabled,
        int failureThreshold,
        Duration openDuration,
        int halfOpenPermits) {

        public CircuitBreakerPolicy {
            if (!enabled) {
                failureThreshold = 1;
                openDuration = Duration.ZERO;
                halfOpenPermits = 1;
            } else {
                if (failureThreshold < 1) {
                    throw new IllegalArgumentException("El umbral del circuit breaker debe ser mayor que cero");
                }
                openDuration = Objects.requireNonNull(openDuration, "openDuration");
                if (openDuration.isNegative()) {
                    throw new IllegalArgumentException("La ventana de apertura del circuit breaker no puede ser negativa");
                }
                if (halfOpenPermits < 1) {
                    throw new IllegalArgumentException("Los intentos half-open deben ser mayores que cero");
                }
            }
        }

        public static CircuitBreakerPolicy disabled() {
            return new CircuitBreakerPolicy(false, 1, Duration.ZERO, 1);
        }
    }

    public record RateLimitPolicy(
        boolean enabled,
        int permits,
        Duration window) {

        public RateLimitPolicy {
            if (!enabled) {
                permits = Integer.MAX_VALUE;
                window = Duration.ofSeconds(1);
            } else {
                if (permits < 1) {
                    throw new IllegalArgumentException("El limite de tasa debe permitir al menos un permiso");
                }
                window = Objects.requireNonNull(window, "window");
                if (window.isNegative() || window.isZero()) {
                    throw new IllegalArgumentException("La ventana del rate limit debe ser mayor que cero");
                }
            }
        }

        public static RateLimitPolicy disabled() {
            return new RateLimitPolicy(false, Integer.MAX_VALUE, Duration.ofSeconds(1));
        }
    }
}
