package com.codearena.api.auth;

import com.codearena.api.auth.dto.AuthResponse;
import com.codearena.api.auth.dto.LoginRequest;
import com.codearena.api.auth.dto.RegisterRequest;
import com.codearena.api.auth.dto.UserResponse;
import com.codearena.api.error.ConflictException;
import com.codearena.api.error.NotFoundException;
import com.codearena.api.error.UnauthorizedException;
import com.codearena.api.security.AuthUser;
import com.codearena.api.security.JwtService;
import com.codearena.api.user.Role;
import com.codearena.api.user.User;
import com.codearena.api.user.UserRepository;
import java.time.Clock;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username is already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }
        User user = userRepository.save(new User(request.username(), email,
                passwordEncoder.encode(request.password()), Role.USER, clock.instant()));
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(AuthUser principal) {
        return userRepository.findById(principal.id())
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private AuthResponse toAuthResponse(User user) {
        String token = jwtService.issue(new AuthUser(user.getId(), user.getUsername(), user.getRole()));
        return new AuthResponse(token, TOKEN_TYPE, jwtService.accessTokenTtl().toSeconds(), UserResponse.from(user));
    }
}
