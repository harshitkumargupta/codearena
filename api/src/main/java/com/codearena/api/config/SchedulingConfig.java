package com.codearena.api.config;

import com.codearena.api.judging.sweeper.SweeperProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(SweeperProperties.class)
public class SchedulingConfig {

    private static final int POOL_SIZE = 3;

    /**
     * Named {@code taskScheduler} so @Scheduled jobs and the leaderboard coalescer use it instead of the STOMP
     * broker's own scheduler, whose presence would otherwise stop Boot from creating a default one.
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix("scheduling-");
        return scheduler;
    }
}
