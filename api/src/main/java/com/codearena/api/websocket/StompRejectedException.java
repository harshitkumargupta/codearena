package com.codearena.api.websocket;

import org.springframework.messaging.MessagingException;

/**
 * Rejects a client frame; the message is safe to show to the client in the STOMP ERROR frame.
 */
public class StompRejectedException extends MessagingException {

    public StompRejectedException(String message) {
        super(message);
    }
}
