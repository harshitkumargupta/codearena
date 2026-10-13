package com.codearena.api.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.client.ResourceAccessException;

class ChatClientTutorModelTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final ChatClientTutorModel model = new ChatClientTutorModel(ChatClient.builder(chatModel));

    @Test
    void sendsSystemAndUserMessagesVerbatimWithoutTemplateRendering() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("  Use a hash map.  "));
        String javaCode = "public class Main { void f() { Map<String, {Integer}> m = new HashMap<>(); } }";

        String answer = model.generate(new TutorPrompt("system rules {level}", "code:\n" + javaCode));

        ArgumentCaptor<Prompt> sent = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(sent.capture());
        List<Message> messages = sent.getValue().getInstructions();
        assertThat(messages).extracting(Message::getMessageType).containsExactly(MessageType.SYSTEM, MessageType.USER);
        assertThat(messages.get(0).getText()).isEqualTo("system rules {level}");
        assertThat(messages.get(1).getText()).isEqualTo("code:\n" + javaCode);
        assertThat(answer).isEqualTo("Use a hash map.");
    }

    @Test
    void transportFailureBecomesUnavailable() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> model.generate(new TutorPrompt("s", "u")))
                .isInstanceOf(TutorUnavailableException.class);
    }

    @Test
    void blankAnswerBecomesUnavailable() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("   "));

        assertThatThrownBy(() -> model.generate(new TutorPrompt("s", "u")))
                .isInstanceOf(TutorUnavailableException.class);
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
