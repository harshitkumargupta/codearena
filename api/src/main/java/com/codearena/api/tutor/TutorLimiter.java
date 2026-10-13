package com.codearena.api.tutor;

import com.codearena.api.error.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis limits: a per-user, per-problem cooldown for level 3 hints and a per-user daily cap (UTC day) on all tutor
 * answers. Capacity is reserved atomically before the model call (SET NX, INCR) so concurrent requests cannot
 * overshoot, and released again if no answer is delivered.
 */
@Component
public class TutorLimiter {

    private static final Logger log = LoggerFactory.getLogger(TutorLimiter.class);
    private static final Duration DAY_KEY_GRACE = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final TutorProperties properties;
    private final Clock clock;

    public TutorLimiter(StringRedisTemplate redis, TutorProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    static String cooldownKey(long userId, long problemId) {
        return "tutor:cooldown:l3:" + userId + ":" + problemId;
    }

    static String dailyKey(long userId, LocalDate day) {
        return "tutor:daily:" + userId + ":" + day;
    }

    public TutorReservation reserve(long userId, long problemId, boolean levelThree) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Instant nextMidnight = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        String cooldownKey = levelThree ? cooldownKey(userId, problemId) : null;
        String dailyKey = dailyKey(userId, today);
        try {
            if (cooldownKey != null
                    && !Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(cooldownKey, "1", properties.level3Cooldown()))) {
                Long ttl = redis.getExpire(cooldownKey, TimeUnit.SECONDS);
                throw new TooManyRequestsException("Level 3 hints for this problem are on cooldown",
                        ttl == null || ttl < 0 ? properties.level3Cooldown().toSeconds() : ttl);
            }
            Long used = redis.opsForValue().increment(dailyKey);
            redis.expireAt(dailyKey, nextMidnight.plus(DAY_KEY_GRACE));
            if (used != null && used > properties.dailyCap()) {
                redis.opsForValue().decrement(dailyKey);
                if (cooldownKey != null) {
                    redis.delete(cooldownKey);
                }
                throw new TooManyRequestsException("Daily limit of " + properties.dailyCap() + " tutor answers reached",
                        Duration.between(now, nextMidnight).toSeconds());
            }
            return new RedisReservation(dailyKey, cooldownKey);
        } catch (DataAccessException ex) {
            log.warn("Tutor limits unavailable (Redis): {}", ex.getClass().getName());
            throw new TutorUnavailableException();
        }
    }

    private final class RedisReservation implements TutorReservation {

        private final String dailyKey;
        private final String cooldownKey;
        private boolean committed;

        private RedisReservation(String dailyKey, String cooldownKey) {
            this.dailyKey = dailyKey;
            this.cooldownKey = cooldownKey;
        }

        @Override
        public void commit() {
            committed = true;
        }

        @Override
        public void close() {
            if (committed) {
                return;
            }
            try {
                redis.opsForValue().decrement(dailyKey);
                if (cooldownKey != null) {
                    redis.delete(cooldownKey);
                }
            } catch (DataAccessException ex) {
                log.warn("Could not release tutor limit reservation: {}", ex.getClass().getName());
            }
        }
    }
}
