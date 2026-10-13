package com.codearena.worker.judge;

import com.codearena.worker.sandbox.SandboxRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JudgeProperties.class)
public class JudgeConfig {

    @Bean
    public JudgeService judgeService(SandboxRunner sandboxRunner, JudgeProperties properties) {
        return new JudgeService(sandboxRunner, properties);
    }
}
