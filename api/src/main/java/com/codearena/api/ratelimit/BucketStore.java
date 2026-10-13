package com.codearena.api.ratelimit;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;

public interface BucketStore {

    /**
     * @throws RuntimeException if the backing store cannot be reached
     */
    ConsumptionProbe tryConsume(String key, BucketConfiguration configuration);
}
