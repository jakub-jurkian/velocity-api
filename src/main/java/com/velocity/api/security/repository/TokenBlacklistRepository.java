package com.velocity.api.security.repository;

import com.velocity.api.security.exception.TokenRevocationUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@Slf4j
@RequiredArgsConstructor
public class TokenBlacklistRepository {
    private final StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "auth:blacklist:";

    public void blacklist(String tokenId, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) return;
        try {
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + tokenId, "blacklisted", ttl);
        } catch (DataAccessException e) {
            // Fail loud: a logout we cannot record must not report success.
            throw new TokenRevocationUnavailableException("Redis unavailable; token " + tokenId + " was NOT revoked", e);
        }
    }

    public boolean isBlacklisted(String tokenId) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PREFIX + tokenId));
        } catch (DataAccessException e) {
            log.error("Redis unavailable; skipping blacklist check (failing open)", e);
            return false;
        }
    }
}
