package com.codearena.api.ratelimit;

import com.codearena.api.error.ServiceUnavailableException;
import com.codearena.api.error.TooManyRequestsException;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Token buckets per user or client address, kept in Redis so all api instances share them. If Redis cannot be
 * reached the limited endpoints fail closed with 503, the same as the tutor limits.
 */
public class RateLimiter {

    static final String UNAVAILABLE = "The service is temporarily unavailable. Please try again in a moment.";

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private static final int MAX_SUBJECT_LENGTH = 100;

    private final BucketStore store;
    private final ClientIpResolver clientIp;
    private final Map<RateLimit, BucketConfiguration> configurations = new EnumMap<>(RateLimit.class);

    public RateLimiter(BucketStore store, ClientIpResolver clientIp, RateLimitProperties properties) {
        this.store = store;
        this.clientIp = clientIp;
        for (RateLimit limit : RateLimit.values()) {
            RateLimitProperties.Limit spec = properties.limit(limit);
            configurations.put(limit, BucketConfiguration.builder()
                    .addLimit(l -> l.capacity(spec.capacity()).refillGreedy(spec.capacity(), spec.period()))
                    .build());
        }
    }

    public void checkSubmission(long userId) {
        consume(RateLimit.SUBMISSIONS, Long.toString(userId));
    }

    public void checkTutor(long userId) {
        consume(RateLimit.TUTOR, Long.toString(userId));
    }

    public void checkLogin(HttpServletRequest request, String username) {
        consume(RateLimit.LOGIN_PER_IP, clientIp.resolve(request));
        consume(RateLimit.LOGIN_PER_USERNAME, normalize(username));
    }

    public void checkRegister(HttpServletRequest request) {
        consume(RateLimit.REGISTER_PER_IP, clientIp.resolve(request));
    }

    void consume(RateLimit limit, String subject) {
        ConsumptionProbe probe;
        try {
            probe = store.tryConsume(limit.redisKey(subject), configurations.get(limit));
        } catch (RuntimeException ex) {
            log.warn("Rate limiter unavailable for {}: {}", limit, ex.getClass().getName());
            throw new ServiceUnavailableException(UNAVAILABLE);
        }
        if (!probe.isConsumed()) {
            long seconds = (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L;
            throw new TooManyRequestsException(limit.message(), seconds);
        }
    }

    private static String normalize(String username) {
        String value = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return value.length() > MAX_SUBJECT_LENGTH ? value.substring(0, MAX_SUBJECT_LENGTH) : value;
    }
}
