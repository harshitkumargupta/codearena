package com.codearena.api.websocket;

import com.codearena.api.security.AuthUser;
import com.codearena.api.security.JwtService;
import java.util.Optional;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP CONNECT frame with the same JWT as the REST API. The token is checked once; the session
 * principal then lives as long as the socket.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    static final String AUTHORIZATION_HEADER = "Authorization";
    static final String AUTHENTICATION_REQUIRED = "Authentication required";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public StompAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getMessageType() != SimpMessageType.CONNECT) {
            return message;
        }
        AuthUser user = token(accessor).flatMap(jwtService::parse)
                .orElseThrow(() -> new StompRejectedException(AUTHENTICATION_REQUIRED));
        accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
        return message;
    }

    private static Optional<String> token(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX) || header.length() == BEARER_PREFIX.length()) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER_PREFIX.length()));
    }
}
