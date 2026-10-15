package com.gamedead.server.auth;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
public class AuthAttemptLimiter {
    public record LimitDecision(boolean allowed,long retryAfterSeconds) {}
    private record Key(String address, String operation) {}
    private static class Window {
        final Instant expires;
        int count;
        Window(Instant expires) { this.expires = expires; }
    }
    private final Clock clock;
    private final int capacity;
    private final Map<Key, Window> windows = new HashMap<>();
    private Instant nextCleanup = Instant.MIN;
    public AuthAttemptLimiter(Clock clock,int capacity) {
        this.clock = clock; this.capacity = capacity;
    }
    public synchronized LimitDecision tryAcquire(String address,String operation) {
        Instant now = clock.instant();
        if (!now.isBefore(nextCleanup)) {
            windows.values().removeIf(window -> !now.isBefore(window.expires));
            nextCleanup = now.plusSeconds(1);
        }
        var key = new Key(address, operation);
        Window window = windows.get(key);
        if (window == null || !now.isBefore(window.expires)) {
            if (window == null && windows.size() >= capacity) return new LimitDecision(false, 1);
            window = new Window(now.plusSeconds(60));
            windows.put(key, window);
        }
        int limit = switch (operation) { case "login" -> 10; case "register" -> 5; default -> throw new IllegalArgumentException("Unknown operation"); };
        if (window.count >= limit) {
            long wait = Math.max(1, (java.time.Duration.between(now, window.expires).toMillis() + 999) / 1000);
            return new LimitDecision(false, wait);
        }
        window.count++;
        return new LimitDecision(true, 0);
    }
}
