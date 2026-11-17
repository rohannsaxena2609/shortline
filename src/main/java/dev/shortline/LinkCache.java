package dev.shortline;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class LinkCache {
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private final StringRedisTemplate redis;
    private final MeterRegistry metrics;

    public LinkCache(StringRedisTemplate redis, MeterRegistry metrics) {
        this.redis = redis;
        this.metrics = metrics;
    }

    public Optional<String> get(String code) {
        try {
            String value = redis.opsForValue().get(key(code));
            metrics.counter("shortline.redirect.cache", "result", value == null ? "miss" : "hit").increment();
            return Optional.ofNullable(value);
        } catch (DataAccessException unavailable) {
            metrics.counter("shortline.redirect.cache", "result", "error").increment();
            return Optional.empty();
        }
    }

    public void put(String code, String destination, Instant expiresAt) {
        try {
            Duration ttl = DEFAULT_TTL;
            if (expiresAt != null) {
                Duration remaining = Duration.between(Instant.now(), expiresAt);
                if (remaining.compareTo(ttl) < 0) ttl = remaining;
            }
            if (!ttl.isNegative() && !ttl.isZero()) redis.opsForValue().set(key(code), destination, ttl);
        } catch (DataAccessException ignored) {
            // The database remains authoritative; cache outages must not fail link creation or redirects.
        }
    }

    public void evict(String code) {
        try {
            redis.delete(key(code));
        } catch (DataAccessException ignored) {
            // Stale entries are bounded by the short cache TTL.
        }
    }

    private String key(String code) { return "shortline:link:" + code; }
}
