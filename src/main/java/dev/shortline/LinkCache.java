package dev.shortline;

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

    public LinkCache(StringRedisTemplate redis) { this.redis = redis; }

    public Optional<String> get(String code) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key(code)));
        } catch (DataAccessException unavailable) {
            return Optional.empty();
        }
    }

    public void put(String code, String destination, Instant expiresAt) {
        try {
            Duration ttl = expiresAt == null ? DEFAULT_TTL : Duration.between(Instant.now(), expiresAt).compareTo(DEFAULT_TTL) < 0
                    ? Duration.between(Instant.now(), expiresAt) : DEFAULT_TTL;
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
