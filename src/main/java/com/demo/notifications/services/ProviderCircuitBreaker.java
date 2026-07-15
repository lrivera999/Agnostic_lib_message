package com.demo.notifications.services;

final class ProviderCircuitBreaker {

    private enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final NotificationResiliencePolicy.CircuitBreakerPolicy policy;
    private State state;
    private int consecutiveFailures;
    private int halfOpenAttempts;
    private long openUntilNanos;

    ProviderCircuitBreaker(NotificationResiliencePolicy.CircuitBreakerPolicy policy) {
        this.policy = policy == null ? NotificationResiliencePolicy.CircuitBreakerPolicy.disabled() : policy;
        this.state = State.CLOSED;
    }

    synchronized boolean allowRequest() {
        if (!policy.enabled()) {
            return true;
        }

        long now = System.nanoTime();
        if (state == State.OPEN) {
            if (now < openUntilNanos) {
                return false;
            }
            state = State.HALF_OPEN;
            halfOpenAttempts = 0;
        }

        if (state == State.HALF_OPEN) {
            if (halfOpenAttempts >= policy.halfOpenPermits()) {
                return false;
            }
            halfOpenAttempts++;
        }

        return true;
    }

    synchronized void recordSuccess() {
        if (!policy.enabled()) {
            return;
        }

        state = State.CLOSED;
        consecutiveFailures = 0;
        halfOpenAttempts = 0;
        openUntilNanos = 0L;
    }

    synchronized boolean recordFailure() {
        if (!policy.enabled()) {
            return false;
        }

        consecutiveFailures++;
        boolean opened = false;
        if (state == State.HALF_OPEN || consecutiveFailures >= policy.failureThreshold()) {
            state = State.OPEN;
            openUntilNanos = System.nanoTime() + policy.openDuration().toNanos();
            halfOpenAttempts = 0;
            opened = true;
        }

        return opened;
    }

    synchronized boolean isOpen() {
        if (!policy.enabled()) {
            return false;
        }

        if (state != State.OPEN) {
            return false;
        }

        return System.nanoTime() < openUntilNanos;
    }
}
