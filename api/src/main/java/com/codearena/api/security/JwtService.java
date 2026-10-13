package com.codearena.api.security;

import com.codearena.api.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String USERNAME_CLAIM = "username";
    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = properties.accessTokenTtl();
        this.clock = clock;
    }

    public String issue(AuthUser user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(Long.toString(user.id()))
                .claim(USERNAME_CLAIM, user.username())
                .claim(ROLE_CLAIM, user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    public Optional<AuthUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AuthUser(
                    Long.parseLong(claims.getSubject()),
                    claims.get(USERNAME_CLAIM, String.class),
                    Role.valueOf(claims.get(ROLE_CLAIM, String.class))));
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }
}
