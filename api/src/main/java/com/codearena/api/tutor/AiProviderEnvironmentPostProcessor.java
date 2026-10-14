package com.codearena.api.tutor;

import java.util.Map;
import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Spring AI's OpenAI auto-configuration refuses to start without an API key. When OpenAI is selected but no key is
 * set, this switches the chat model off instead, so the api starts and only the tutor answers 503.
 */
public class AiProviderEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String CHAT_MODEL = "spring.ai.model.chat";

    private final Log log;

    public AiProviderEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(AiProviderEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean openAi = "openai".equalsIgnoreCase(environment.getProperty(CHAT_MODEL));
        boolean hasKey = StringUtils.hasText(environment.getProperty("spring.ai.openai.api-key"))
                || StringUtils.hasText(environment.getProperty("spring.ai.openai.chat.api-key"));
        if (openAi && !hasKey) {
            environment.getPropertySources().addFirst(
                    new MapPropertySource("codearenaAiWithoutKey", Map.of(CHAT_MODEL, "none")));
            log.warn("AI_CHAT_PROVIDER is openai but OPENAI_API_KEY is empty; the AI tutor is disabled");
        }
    }
}
