package com.codearena.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codearena.api.auth.dto.AuthResponse;
import com.codearena.api.auth.dto.UserResponse;
import com.codearena.api.security.AuthUser;
import com.codearena.api.security.JwtService;
import com.codearena.api.support.WebSecurityTestConfig;
import com.codearena.api.user.Role;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class AuthControllerTest {

    private static final UserResponse ALICE =
            new UserResponse(1L, "alice", "alice@example.com", Role.USER, Instant.parse("2026-10-13T10:00:00Z"));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @Test
    void registerWithInvalidBodyReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "a!", "email": "not-an-email", "password": "short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        Matchers.hasItems("username", "email", "password")));
    }

    @Test
    void malformedJsonReturnsErrorFormat() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void loginIsPublic() throws Exception {
        when(authService.login(any())).thenReturn(new AuthResponse("token", "Bearer", 900, ALICE));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "alice", "password": "password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token"));
    }

    @Test
    void meWithoutTokenReturns401Json() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"));
    }

    @Test
    void meWithInvalidTokenReturns401() throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithValidTokenReturnsUser() throws Exception {
        AuthUser principal = new AuthUser(1L, "alice", Role.USER);
        when(authService.me(principal)).thenReturn(ALICE);

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}
