package com.codearena.api.web;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("codearena.http")
public record HttpLimitsProperties(@Min(1024) long maxBodyBytes, @Min(1024) long maxTestCaseBodyBytes) {
}
