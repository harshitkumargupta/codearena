package com.codearena.api.problem;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code maxTestDataBytes} bounds the input plus expected output of all test cases of one problem, because every
 * judge job carries all of them and must stay well under RabbitMQ's 128 MiB message limit.
 */
@Validated
@ConfigurationProperties("codearena.problems")
public record ProblemLimitsProperties(@Min(1) long maxTestDataBytes) {
}
