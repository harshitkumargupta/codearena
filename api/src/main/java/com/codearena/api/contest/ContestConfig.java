package com.codearena.api.contest;

import com.codearena.api.contest.leaderboard.LeaderboardProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LeaderboardProperties.class)
public class ContestConfig {
}
