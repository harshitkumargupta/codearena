package com.codearena.api.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codearena.api.security.AuthUser;
import com.codearena.api.security.JwtProperties;
import com.codearena.api.security.JwtService;
import com.codearena.api.user.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;

class StompAuthInterceptorTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");
    private static final Duration TTL = Duration.ofHours(2);
    private static final AuthUser ALICE = new AuthUser(1L, "alice", Role.USER);

    private final JwtService jwtService = jwtAt(NOW);
    private final StompAuthInterceptor interceptor = new StompAuthInterceptor(jwtService);

    @Test
    void validTokenSetsSessionPrincipal() {
        Message<?> result = interceptor.preSend(StompFrames.connect("Bearer " + jwtService.issue(ALICE)),
                StompFrames.CHANNEL);

        Authentication user = (Authentication) StompHeaderAccessor.wrap(result).getUser();
        assertThat(user.getPrincipal()).isEqualTo(ALICE);
        assertThat(user.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    void missingTokenIsRejected() {
        assertRejected(StompFrames.connect(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer", "Bearer ", "Basic abc", "Bearer not-a-jwt", "bearer x.y.z"})
    void malformedTokenIsRejected(String header) {
        assertRejected(StompFrames.connect(header));
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = jwtAt(NOW.minus(TTL).minusSeconds(1)).issue(ALICE);

        assertRejected(StompFrames.connect("Bearer " + expired));
    }

    @Test
    void tokenSignedWithOtherSecretIsRejected() {
        String forged = new JwtService(new JwtProperties("ffffffffffffffffffffffffffffffff", TTL),
                Clock.fixed(NOW, ZoneOffset.UTC)).issue(new AuthUser(1L, "alice", Role.ADMIN));

        assertRejected(StompFrames.connect("Bearer " + forged));
    }

    @Test
    void framesOtherThanConnectPassThrough() {
        Message<byte[]> subscribe = StompFrames.of(StompCommand.SUBSCRIBE);

        assertThat(interceptor.preSend(subscribe, StompFrames.CHANNEL)).isSameAs(subscribe);
    }

    private void assertRejected(Message<?> frame) {
        assertThatThrownBy(() -> interceptor.preSend(frame, StompFrames.CHANNEL))
                .isInstanceOf(StompRejectedException.class)
                .hasMessage("Authentication required");
    }

    private static JwtService jwtAt(Instant instant) {
        return new JwtService(new JwtProperties(SECRET, TTL), Clock.fixed(instant, ZoneOffset.UTC));
    }
}
