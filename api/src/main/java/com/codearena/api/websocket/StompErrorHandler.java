package com.codearena.api.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * Builds the STOMP ERROR frame from a safe message only; Spring otherwise copies the raw exception message.
 * The server closes the connection after sending it.
 */
@Component
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    static final String INTERNAL_ERROR = "Internal error";

    private static final Logger log = LoggerFactory.getLogger(StompErrorHandler.class);

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        StompHeaderAccessor client = clientMessage == null ? null
                : MessageHeaderAccessor.getAccessor(clientMessage, StompHeaderAccessor.class);
        String command = client == null || client.getCommand() == null ? "unknown" : client.getCommand().name();
        String destination = client == null ? null : client.getDestination();
        StompRejectedException rejection = findRejection(ex);
        if (rejection != null) {
            log.info("STOMP {} rejected (destination={}): {}", command, destination, rejection.getMessage());
            return super.handleClientMessageProcessingError(clientMessage, new MessagingException(rejection.getMessage()));
        }
        log.warn("STOMP {} failed (destination={}): {}", command, destination, ex.getClass().getSimpleName());
        return super.handleClientMessageProcessingError(clientMessage, new MessagingException(INTERNAL_ERROR));
    }

    private static StompRejectedException findRejection(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof StompRejectedException rejection) {
                return rejection;
            }
        }
        return null;
    }
}
