package com.codearena.api.config;

import com.codearena.api.judging.sweeper.SweeperProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(SweeperProperties.class)
public class SchedulingConfig {
}
