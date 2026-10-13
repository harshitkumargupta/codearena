package com.codearena.worker.judge;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("codearena.judge")
public record JudgeProperties(@Min(100) int detailMaxChars) {
}
