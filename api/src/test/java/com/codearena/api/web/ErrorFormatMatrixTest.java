package com.codearena.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.codearena.api.problem.AdminProblemController;
import com.codearena.api.problem.AdminProblemService;
import com.codearena.api.problem.ProblemController;
import com.codearena.api.problem.ProblemService;
import com.codearena.api.ratelimit.RateLimiter;
import com.codearena.api.security.AuthUser;
import com.codearena.api.security.JwtService;
import com.codearena.api.submission.SubmissionController;
import com.codearena.api.submission.SubmissionService;
import com.codearena.api.support.WebSecurityTestConfig;
import com.codearena.api.user.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Every kind of failure returns the same JSON body: timestamp, status, error, message and path.
 */
@WebMvcTest(controllers = {SubmissionController.class, ProblemController.class, AdminProblemController.class})
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class ErrorFormatMatrixTest {

    private static final String USER = "user";
    private static final String NONE = "none";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SubmissionService submissionService;

    @MockitoBean
    private ProblemService problemService;

    @MockitoBean
    private AdminProblemService adminProblemService;

    @MockitoBean
    private RateLimiter rateLimiter;

    @BeforeEach
    void stubSuccessfulRead() {
        when(submissionService.mine(any(), isNull(), anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
    }

    static Stream<Arguments> failures() {
        Function<MockHttpServletRequestBuilder, MockHttpServletRequestBuilder> json = b -> b.contentType(MediaType.APPLICATION_JSON);
        return Stream.of(
                Arguments.of("malformed JSON", USER, json.apply(post("/api/submissions")).content("{not json"), 400, "Malformed request body"),
                Arguments.of("wrong media type", USER, post("/api/submissions").contentType(MediaType.TEXT_PLAIN).content("x"), 415, "Unsupported content type"),
                Arguments.of("not acceptable", USER, get("/api/submissions/me").accept(MediaType.TEXT_PLAIN), 406, "Only application/json responses are available"),
                Arguments.of("unknown route", USER, get("/api/does-not-exist"), 404, "Resource not found"),
                Arguments.of("wrong method", USER, delete("/api/submissions/me"), 405, "Method not allowed"),
                Arguments.of("oversized body", USER, json.apply(post("/api/submissions")).content("x".repeat(300_000)), 413, "Request body is too large (limit 256 KiB)"),
                Arguments.of("validation", USER, json.apply(post("/api/submissions")).content("{}"), 400, "Validation failed"),
                Arguments.of("missing token", NONE, get("/api/submissions/me"), 401, "Authentication required"),
                Arguments.of("forbidden role", USER, json.apply(post("/api/admin/problems")).content("{}"), 403, "Access denied"),
                Arguments.of("type mismatch", USER, get("/api/submissions/abc"), 400, "Invalid value for parameter 'id'"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failures")
    void everyFailureUsesTheStandardErrorBody(String name, String auth, MockHttpServletRequestBuilder request, int status,
                                              String message) throws Exception {
        if (USER.equals(auth)) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(new AuthUser(1L, "alice", Role.USER)));
        }

        MvcResult result = mvc.perform(request).andReturn();

        assertThat(result.getResponse().getStatus()).as(name).isEqualTo(status);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.fieldNames()).toIterable().contains("timestamp", "status", "error", "message", "path");
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("message").asText()).isEqualTo(message);
        assertThat(body.get("path").asText()).isEqualTo(result.getRequest().getRequestURI());
        if (status == 400 && "validation".equals(name)) {
            assertThat(body.get("fieldErrors")).isNotEmpty();
        }
    }
}
