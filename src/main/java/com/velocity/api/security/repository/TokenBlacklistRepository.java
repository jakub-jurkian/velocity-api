package com.velocity.api.security.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class TokenBlacklistRepository {
    private final StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "auth:blacklist:";

    public void blacklist(String token, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) return;
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + token, "blacklisted", ttl);
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PREFIX + token));
    }

    public void blacklistUser(String userId, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) return;
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + "user:" + userId, "blacklisted", ttl);
    }

    public boolean isUserBlacklisted(String userId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PREFIX + "user:" + userId));

    }
}
