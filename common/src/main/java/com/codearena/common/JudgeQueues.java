package com.codearena.common;

import java.util.Map;

/**
 * Queue topology shared by api and worker. Both services declare these queues with exactly these arguments,
 * because RabbitMQ rejects a redeclaration whose arguments differ.
 */
public final class JudgeQueues {

    public static final String JOBS = "judge.jobs";
    public static final String RESULTS = "judge.results";
    public static final String JOBS_DLQ = "judge.jobs.dlq";

    public static final int MAX_ATTEMPTS = 3;

    // A quorum queue dead-letters once a message has been returned more than x-delivery-limit times,
    // so a limit of MAX_ATTEMPTS - 1 allows exactly MAX_ATTEMPTS deliveries (verified on RabbitMQ 3.13).
    public static final int DELIVERY_LIMIT = MAX_ATTEMPTS - 1;

    public static final Map<String, Object> JOBS_ARGUMENTS = Map.of(
            "x-queue-type", "quorum",
            "x-delivery-limit", DELIVERY_LIMIT,
            "x-dead-letter-exchange", "",
            "x-dead-letter-routing-key", JOBS_DLQ);

    public static final Map<String, Object> RESULTS_ARGUMENTS = Map.of(
            "x-queue-type", "quorum",
            "x-delivery-limit", DELIVERY_LIMIT);

    public static final Map<String, Object> JOBS_DLQ_ARGUMENTS = Map.of(
            "x-queue-type", "quorum");

    public static final String TRUSTED_PACKAGE = "com.codearena.common";

    private JudgeQueues() {
    }
}
