package com.codearena.api.tutor;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * Sends prompts through Spring AI's ChatClient; the provider (Ollama or an OpenAI-compatible endpoint) is chosen by
 * {@code spring.ai.model.chat}. Messages are passed as objects so user code is never parsed as a prompt template.
 */
@Component
public class ChatClientTutorModel implements TutorModel {

    private static final Logger log = LoggerFactory.getLogger(ChatClientTutorModel.class);

    private final ChatClient chatClient;

    public ChatClientTutorModel(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String generate(TutorPrompt prompt) {
        String content;
        try {
            content = chatClient
                    .prompt(new Prompt(List.of(new SystemMessage(prompt.system()), new UserMessage(prompt.user()))))
                    .call()
                    .content();
        } catch (RuntimeException ex) {
            log.warn("AI tutor model call failed: {}", describe(ex));
            throw new TutorUnavailableException();
        }
        if (content == null || content.isBlank()) {
            log.warn("AI tutor model returned an empty answer");
            throw new TutorUnavailableException();
        }
        return content.strip();
    }

    private static String describe(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root == ex ? ex.getClass().getName() : ex.getClass().getName() + " caused by " + root.getClass().getName();
    }
}
