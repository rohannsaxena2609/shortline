package dev.shortline;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class LinkCache {
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final long FAILURE_COOLDOWN_MILLIS = 5_000;
    private final StringRedisTemplate redis;
    private final MeterRegistry metrics;
    private final AtomicLong unavailableUntil = new AtomicLong();

    public LinkCache(StringRedisTemplate redis, MeterRegistry metrics) {
        this.redis = redis;
        this.metrics = metrics;
    }

    public Optional<String> get(String code) {
        if (isCoolingDown()) return cacheMiss("unavailable");
        try {
            String value = redis.opsForValue().get(key(code));
            metrics.counter("shortline.redirect.cache", "result", value == null ? "miss" : "hit").increment();
            return Optional.ofNullable(value);
        } catch (DataAccessException unavailable) {
            markUnavailable();
            return cacheMiss("error");
        }
    }

    public void put(String code, String destination, Instant expiresAt) {
        if (isCoolingDown()) return;
        try {
            Duration ttl = DEFAULT_TTL;
            if (expiresAt != null) {
                Duration remaining = Duration.between(Instant.now(), expiresAt);
                if (remaining.compareTo(ttl) < 0) ttl = remaining;
            }
            if (!ttl.isNegative() && !ttl.isZero()) redis.opsForValue().set(key(code), destination, ttl);
        } catch (DataAccessException ignored) {
            markUnavailable();
        }
    }

    public void evict(String code) {
        if (isCoolingDown()) return;
        try {
            redis.delete(key(code));
        } catch (DataAccessException ignored) {
            markUnavailable();
        }
    }

    private Optional<String> cacheMiss(String result) {
        metrics.counter("shortline.redirect.cache", "result", result).increment();
        return Optional.empty();
    }

    private boolean isCoolingDown() { return System.currentTimeMillis() < unavailableUntil.get(); }

    private void markUnavailable() {
        unavailableUntil.set(System.currentTimeMillis() + FAILURE_COOLDOWN_MILLIS);
    }

    private String key(String code) { return "shortline:link:" + code; }
}
