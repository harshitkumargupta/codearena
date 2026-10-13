package com.codearena.api.ratelimit;

import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    public RedisBucketStore bucketStore(RedisProperties redis, RateLimitProperties properties) {
        return new RedisBucketStore(redis.getHost(), redis.getPort(), redis.getPassword(), properties.redisTimeout());
    }

    @Bean
    public ClientIpResolver clientIpResolver(RateLimitProperties properties) {
        return new ClientIpResolver(properties.trustedProxies());
    }

    @Bean
    public RateLimiter rateLimiter(BucketStore bucketStore, ClientIpResolver clientIpResolver,
                                   RateLimitProperties properties) {
        return new RateLimiter(bucketStore, clientIpResolver, properties);
    }
}
