package com.codearena.api.judging;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("codearena.messaging")
public record MessagingProperties(@NotNull Duration confirmTimeout) {
}
