package com.codearena.api.websocket;

import com.codearena.api.security.AuthUser;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

final class StompFrames {

    static final MessageChannel CHANNEL = (message, timeout) -> true;

    private StompFrames() {
    }

    static Message<byte[]> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.setNativeHeader(StompAuthInterceptor.AUTHORIZATION_HEADER, authorization);
        }
        return build(accessor);
    }

    static Message<byte[]> subscribe(AuthUser user, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSubscriptionId("sub-0");
        if (user != null) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
        }
        return build(accessor);
    }

    static Message<byte[]> send(AuthUser user, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
        return build(accessor);
    }

    static Message<byte[]> of(StompCommand command) {
        return build(StompHeaderAccessor.create(command));
    }

    private static Message<byte[]> build(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
