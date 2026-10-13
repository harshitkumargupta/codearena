package com.codearena.api.submission;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codearena.api.security.AuthUser;
import com.codearena.api.security.JwtService;
import com.codearena.api.submission.dto.SubmitRequest;
import com.codearena.api.submission.dto.SubmitResponse;
import com.codearena.api.support.WebSecurityTestConfig;
import com.codearena.api.user.Role;
import com.codearena.common.Verdict;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(SubmissionController.class)
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class SubmissionControllerTest {

    private static final AuthUser ALICE = new AuthUser(1L, "alice", Role.USER);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SubmissionService submissionService;

    @Test
    void validSubmissionReturnsPendingId() throws Exception {
        when(submissionService.submit(eq(ALICE), any())).thenReturn(new SubmitResponse(10L, Verdict.PENDING));

        submit(Map.of("problemId", 1, "language", "JAVA", "sourceCode", "class Main {}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void sourceOver64KbIsRejected() throws Exception {
        submit(Map.of("problemId", 1, "language", "PYTHON",
                "sourceCode", "x".repeat(SubmitRequest.MAX_SOURCE_BYTES + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sourceCode"));
        verifyNoInteractions(submissionService);
    }

    @Test
    void sourceLimitCountsUtf8BytesNotCharacters() throws Exception {
        String multiByte = "é".repeat(SubmitRequest.MAX_SOURCE_BYTES / 2 + 1);

        submit(Map.of("problemId", 1, "language", "PYTHON", "sourceCode", multiByte))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sourceCode"));
    }

    @Test
    void sourceExactlyAtLimitIsAccepted() throws Exception {
        when(submissionService.submit(eq(ALICE), any())).thenReturn(new SubmitResponse(11L, Verdict.PENDING));

        submit(Map.of("problemId", 1, "language", "PYTHON", "sourceCode", "x".repeat(SubmitRequest.MAX_SOURCE_BYTES)))
                .andExpect(status().isCreated());
    }

    @Test
    void unknownLanguageIsRejectedWithAllowedValues() throws Exception {
        submit(Map.of("problemId", 1, "language", "RUBY", "sourceCode", "puts 1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("language"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must be one of: JAVA, PYTHON"));
    }

    @Test
    void missingFieldsAreRejected() throws Exception {
        submit(Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
    }

    @Test
    void submitWithoutTokenGets401() throws Exception {
        mvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":1,\"language\":\"JAVA\",\"sourceCode\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions submit(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/submissions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(ALICE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }
}
