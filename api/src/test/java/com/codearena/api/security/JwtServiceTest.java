package com.codearena.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.codearena.api.user.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");
    private static final Duration TTL = Duration.ofHours(2);
    private static final AuthUser ALICE = new AuthUser(42L, "alice", Role.ADMIN);

    private static JwtService serviceAt(Instant instant, String secret) {
        return new JwtService(new JwtProperties(secret, TTL), Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void issuedTokenRoundTrips() {
        JwtService service = serviceAt(NOW, SECRET);

        assertThat(service.parse(service.issue(ALICE))).contains(ALICE);
    }

    @Test
    void expiredTokenIsRejected() {
        String token = serviceAt(NOW, SECRET).issue(ALICE);

        assertThat(serviceAt(NOW.plus(TTL).plusSeconds(1), SECRET).parse(token)).isEmpty();
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService service = serviceAt(NOW, SECRET);
        String token = service.issue(ALICE);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThat(service.parse(tampered)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = serviceAt(NOW, "another-secret-another-secret-0123").issue(ALICE);

        assertThat(serviceAt(NOW, SECRET).parse(token)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(serviceAt(NOW, SECRET).parse("not-a-jwt")).isEmpty();
    }
}
