package com.uniikm.configmanager.cache;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Чёрный список отозванных токенов (logout, ротация refresh-токена).
 * Идентификатор токена (jti) хранится в Redis с TTL, равным остатку срока
 * действия токена — после естественного истечения запись удаляется сама.
 *
 * <p>Часть модуля {@code cache}: единственного места доступа к Redis.
 */
@Component
public class TokenBlacklistStore {

    private static final String PREFIX = "auth:blacklist:";

    private final StringRedisTemplate redis;

    public TokenBlacklistStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Помещает токен в чёрный список до момента его истечения. */
    public void blacklist(String jti, Instant expiry) {
        if (jti == null) {
            return;
        }
        Duration ttl = Duration.between(Instant.now(), expiry);
        if (ttl.isNegative() || ttl.isZero()) {
            return; // токен уже истёк — хранить не нужно
        }
        redis.opsForValue().set(PREFIX + jti, "revoked", ttl);
    }

    /** Проверяет, отозван ли токен с данным jti. */
    public boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
    }
}
