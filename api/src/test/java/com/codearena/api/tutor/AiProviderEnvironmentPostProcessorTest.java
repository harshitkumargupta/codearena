package com.codearena.api.tutor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class AiProviderEnvironmentPostProcessorTest {

    private final AiProviderEnvironmentPostProcessor processor =
            new AiProviderEnvironmentPostProcessor(Supplier::get);

    @Test
    void openAiWithoutKeyTurnsTheChatModelOff() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.ai.model.chat", "openai")
                .withProperty("spring.ai.openai.api-key", "");

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("spring.ai.model.chat")).isEqualTo("none");
    }

    @ParameterizedTest
    @CsvSource({"openai,sk-test", "ollama,", "none,"})
    void otherSettingsAreLeftAlone(String provider, String key) {
        MockEnvironment env = new MockEnvironment().withProperty("spring.ai.model.chat", provider);
        if (key != null) {
            env.setProperty("spring.ai.openai.api-key", key);
        }

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("spring.ai.model.chat")).isEqualTo(provider);
    }
}
