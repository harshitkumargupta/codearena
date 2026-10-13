package com.codearena.api.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

class StompErrorHandlerTest {

    private final StompErrorHandler handler = new StompErrorHandler();

    @Test
    void rejectionMessageIsPassedThrough() {
        Message<byte[]> error = handler.handleClientMessageProcessingError(StompFrames.connect(null),
                new MessageDeliveryException(StompFrames.connect(null), new StompRejectedException("Authentication required")));

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(error);
        assertThat(accessor.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(accessor.getMessage()).isEqualTo("Authentication required");
    }

    @Test
    void unexpectedErrorsNeverExposeTheirMessage() {
        Message<byte[]> error = handler.handleClientMessageProcessingError(StompFrames.of(StompCommand.SUBSCRIBE),
                new IllegalStateException("JDBC connection to db-internal:5432 failed"));

        assertThat(StompHeaderAccessor.wrap(error).getMessage()).isEqualTo("Internal error");
    }
}
