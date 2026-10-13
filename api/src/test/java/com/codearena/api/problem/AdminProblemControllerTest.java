package com.codearena.api.problem;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codearena.api.problem.dto.AdminProblemResponse;
import com.codearena.api.problem.dto.ProblemRequest;
import com.codearena.api.security.AuthUser;
import com.codearena.api.security.JwtService;
import com.codearena.api.support.WebSecurityTestConfig;
import com.codearena.api.user.Role;
import java.time.Instant;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminProblemController.class)
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class AdminProblemControllerTest {

    private static final String VALID_BODY = """
            {"slug": "two-sum", "title": "Two Sum", "statementMd": "Find two numbers.",
             "difficulty": "EASY", "timeLimitMs": 2000, "memoryLimitMb": 256, "published": true}
            """;
    private static final ProblemRequest VALID_REQUEST =
            new ProblemRequest("two-sum", "Two Sum", "Find two numbers.", Difficulty.EASY, 2000, 256, true);
    private static final AdminProblemResponse CREATED = new AdminProblemResponse(1L, "two-sum", "Two Sum",
            "Find two numbers.", Difficulty.EASY, 2000, 256, true, Instant.parse("2026-10-13T10:00:00Z"));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AdminProblemController controller;

    @MockitoBean
    private AdminProblemService adminProblemService;

    @Test
    void noTokenGets401() throws Exception {
        mvc.perform(post("/api/admin/problems").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(adminProblemService);
    }

    @Test
    void userTokenGets403Json() throws Exception {
        mvc.perform(post("/api/admin/problems")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.path").value("/api/admin/problems"));
        verifyNoInteractions(adminProblemService);
    }

    @Test
    void userTokenGets403OnTestCaseUpload() throws Exception {
        mvc.perform(post("/api/admin/problems/1/testcases")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"testCases": [{"input": "1", "expectedOutput": "1", "sample": false}]}
                                """))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminProblemService);
    }

    @Test
    void adminTokenCreatesProblem() throws Exception {
        when(adminProblemService.create(eq(VALID_REQUEST), eq(99L))).thenReturn(CREATED);

        mvc.perform(post("/api/admin/problems")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("two-sum"));
    }

    @Test
    void adminGetsValidationErrors() throws Exception {
        mvc.perform(put("/api/admin/problems/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug": "Bad Slug", "title": "", "statementMd": "x", "difficulty": "EASY",
                                 "timeLimitMs": 5, "memoryLimitMb": 256}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
    }

    @Test
    @WithMockUser(roles = "USER")
    void methodSecurityRejectsNonAdminEvenWithoutUrlRules() {
        AuthUser user = new AuthUser(5L, "bob", Role.USER);

        assertThatThrownBy(() -> controller.create(VALID_REQUEST, user)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(adminProblemService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void methodSecurityAllowsAdmin() {
        when(adminProblemService.update(anyLong(), any())).thenReturn(CREATED);

        Assertions.assertThat(controller.update(1L, VALID_REQUEST)).isEqualTo(CREATED);
    }

    private String bearer(Role role) {
        return "Bearer " + jwtService.issue(new AuthUser(99L, role.name().toLowerCase(), role));
    }
}
