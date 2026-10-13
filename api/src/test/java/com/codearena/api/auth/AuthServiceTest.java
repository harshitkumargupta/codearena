package com.codearena.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codearena.api.auth.dto.AuthResponse;
import com.codearena.api.auth.dto.LoginRequest;
import com.codearena.api.auth.dto.RegisterRequest;
import com.codearena.api.error.ConflictException;
import com.codearena.api.error.UnauthorizedException;
import com.codearena.api.security.AuthUser;
import com.codearena.api.security.JwtProperties;
import com.codearena.api.security.JwtService;
import com.codearena.api.user.Role;
import com.codearena.api.user.User;
import com.codearena.api.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final JwtService jwtService =
            new JwtService(new JwtProperties("0123456789abcdef0123456789abcdef", Duration.ofHours(2)), clock);
    private final AuthService authService = new AuthService(userRepository, passwordEncoder, jwtService, clock);

    @Test
    void registerHashesPasswordAndIssuesUserToken() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0), 7L));

        AuthResponse response = authService.register(new RegisterRequest("alice", "Alice@Example.com", "password123"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getValue().getPasswordHash())).isTrue();
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(saved.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(jwtService.parse(response.accessToken())).contains(new AuthUser(7L, "alice", Role.USER));
        assertThat(response.expiresInSeconds()).isEqualTo(7200);
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", "a@example.com", "password123")))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("a@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", "A@example.com", "password123")))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginSucceedsWithCorrectPassword() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(storedUser("password123")));

        AuthResponse response = authService.login(new LoginRequest("alice", "password123"));

        assertThat(jwtService.parse(response.accessToken())).contains(new AuthUser(7L, "alice", Role.USER));
    }

    @Test
    void loginRejectsWrongPassword() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(storedUser("password123")));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong-password")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void loginRejectsUnknownUserWithSameMessage() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "password123")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid username or password");
    }

    private User storedUser(String rawPassword) {
        return withId(new User("alice", "alice@example.com", passwordEncoder.encode(rawPassword), Role.USER,
                clock.instant()), 7L);
    }

    private static User withId(User user, long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
