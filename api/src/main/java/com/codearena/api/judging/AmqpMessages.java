package com.codearena.api.judging;

import java.util.Optional;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.MessageConverter;

final class AmqpMessages {

    private AmqpMessages() {
    }

    static <T> Optional<T> read(MessageConverter converter, Message message, Class<T> type) {
        try {
            Object body = converter.fromMessage(message);
            return type.isInstance(body) ? Optional.of(type.cast(body)) : Optional.empty();
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    static String describe(Message message) {
        return "messageId=" + message.getMessageProperties().getMessageId()
                + ", type=" + message.getMessageProperties().getHeader("__TypeId__");
    }
}
