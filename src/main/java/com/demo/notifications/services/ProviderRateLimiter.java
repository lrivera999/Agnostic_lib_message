package com.demo.notifications.services;

final class ProviderRateLimiter {

    private final NotificationResiliencePolicy.RateLimitPolicy policy;
    private long windowStartNanos;
    private int usedPermits;

    ProviderRateLimiter(NotificationResiliencePolicy.RateLimitPolicy policy) {
        this.policy = policy == null ? NotificationResiliencePolicy.RateLimitPolicy.disabled() : policy;
    }

    synchronized boolean tryAcquire() {
        if (!policy.enabled()) {
            return true;
        }

        long windowNanos = policy.window().toNanos();
        long now = System.nanoTime();
        if (windowStartNanos == 0L || now - windowStartNanos >= windowNanos) {
            windowStartNanos = now;
            usedPermits = 0;
        }

        if (usedPermits >= policy.permits()) {
            return false;
        }

        usedPermits++;
        return true;
    }
}
