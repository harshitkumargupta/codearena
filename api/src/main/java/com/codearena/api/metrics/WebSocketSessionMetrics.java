package com.codearena.api.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Connected STOMP sessions on this api instance. Tracked as a set of session ids because Spring may publish more
 * than one disconnect event for a session.
 */
@Component
public class WebSocketSessionMetrics {

    public static final String SESSIONS = "codearena.websocket.sessions";

    private final Set<String> sessions = ConcurrentHashMap.newKeySet();

    public WebSocketSessionMetrics(MeterRegistry registry) {
        Gauge.builder(SESSIONS, sessions, Set::size)
                .description("Connected STOMP sessions on this api instance")
                .register(registry);
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        String id = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (id != null) {
            sessions.add(id);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        sessions.remove(event.getSessionId());
    }
}
