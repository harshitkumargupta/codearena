package com.codearena.api.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

class WebSocketSessionMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final WebSocketSessionMetrics metrics = new WebSocketSessionMetrics(registry);

    @Test
    void countsConnectedSessionsAndToleratesRepeatedDisconnects() {
        metrics.onConnected(new SessionConnectedEvent(this, message("s1")));
        metrics.onConnected(new SessionConnectedEvent(this, message("s2")));
        assertThat(sessions()).isEqualTo(2.0);

        metrics.onDisconnect(new SessionDisconnectEvent(this, message("s1"), "s1", CloseStatus.NORMAL));
        metrics.onDisconnect(new SessionDisconnectEvent(this, message("s1"), "s1", CloseStatus.NORMAL));
        metrics.onDisconnect(new SessionDisconnectEvent(this, message("never-connected"), "never-connected",
                CloseStatus.NORMAL));

        assertThat(sessions()).isEqualTo(1.0);
    }

    private double sessions() {
        return registry.get(WebSocketSessionMetrics.SESSIONS).gauge().value();
    }

    private static Message<byte[]> message(String sessionId) {
        return MessageBuilder.createMessage(new byte[0],
                new MessageHeaders(Map.of(SimpMessageHeaderAccessor.SESSION_ID_HEADER,
                        sessionId)));
    }
}
