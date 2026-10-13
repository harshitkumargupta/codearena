package com.codearena.api.ratelimit;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.time.Duration;
import org.springframework.beans.factory.DisposableBean;

/**
 * Bucket4j buckets in Redis over a dedicated Lettuce connection. The connection is opened lazily, so the api starts
 * without Redis, and it rejects commands while disconnected, so a Redis outage fails fast instead of queueing.
 */
public class RedisBucketStore implements BucketStore, DisposableBean {

    private static final Duration KEEP_FULL_BUCKETS_FOR = Duration.ofSeconds(10);

    private final RedisURI uri;
    private final Duration timeout;
    private RedisClient client;
    private StatefulRedisConnection<String, byte[]> connection;
    private ProxyManager<String> proxyManager;

    public RedisBucketStore(String host, int port, String password, Duration timeout) {
        RedisURI.Builder builder = RedisURI.builder().withHost(host).withPort(port).withTimeout(timeout);
        if (password != null && !password.isEmpty()) {
            builder.withPassword(password.toCharArray());
        }
        this.uri = builder.build();
        this.timeout = timeout;
    }

    @Override
    public ConsumptionProbe tryConsume(String key, BucketConfiguration configuration) {
        return proxyManager().getProxy(key, () -> configuration).tryConsumeAndReturnRemaining(1);
    }

    private synchronized ProxyManager<String> proxyManager() {
        if (proxyManager != null) {
            return proxyManager;
        }
        RedisClient created = RedisClient.create(uri);
        created.setOptions(ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .timeoutOptions(TimeoutOptions.enabled(timeout))
                .build());
        try {
            connection = created.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        } catch (RuntimeException ex) {
            created.shutdown();
            throw ex;
        }
        client = created;
        proxyManager = Bucket4jLettuce.casBasedBuilder(connection)
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(KEEP_FULL_BUCKETS_FOR))
                .build();
        return proxyManager;
    }

    @Override
    public synchronized void destroy() {
        if (connection != null) {
            connection.close();
        }
        if (client != null) {
            client.shutdown();
        }
    }
}
