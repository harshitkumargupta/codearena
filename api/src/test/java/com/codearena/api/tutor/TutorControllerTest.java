package com.codearena.api.tutor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codearena.api.error.ConflictException;
import com.codearena.api.error.ForbiddenException;
import com.codearena.api.error.TooManyRequestsException;
import com.codearena.api.security.AuthUser;
import com.codearena.api.security.JwtService;
import com.codearena.api.support.WebSecurityTestConfig;
import com.codearena.api.tutor.dto.TutorResponse;
import com.codearena.api.user.Role;
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

@WebMvcTest(TutorController.class)
@Import(WebSecurityTestConfig.class)
@ActiveProfiles("test")
class TutorControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TutorService tutorService;

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/tutor/hint").contentType(MediaType.APPLICATION_JSON).content("{\"problemId\":1,\"level\":1}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(tutorService);
    }

    @Test
    void deliversHint() throws Exception {
        when(tutorService.hint(any(), any())).thenReturn(new TutorResponse(HintKind.HINT, 1, "Think hash map.", false));

        hint("{\"problemId\":1,\"level\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("HINT"))
                .andExpect(jsonPath("$.text").value("Think hash map."))
                .andExpect(jsonPath("$.fallback").value(false));
    }

    @Test
    void levelOutsideOneToThreeIsRejected() throws Exception {
        hint("{\"problemId\":1,\"level\":4}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("level"));
        verifyNoInteractions(tutorService);
    }

    @Test
    void limitHitIs429WithRetryAfter() throws Exception {
        when(tutorService.hint(any(), any()))
                .thenThrow(new TooManyRequestsException("Level 3 hints for this problem are on cooldown", 287));

        hint("{\"problemId\":1,\"level\":3}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "287"))
                .andExpect(jsonPath("$.message").value("Level 3 hints for this problem are on cooldown"));
    }

    @Test
    void unavailableModelIs503() throws Exception {
        when(tutorService.hint(any(), any())).thenThrow(new TutorUnavailableException());

        hint("{\"problemId\":1,\"level\":1}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(TutorUnavailableException.MESSAGE));
    }

    @Test
    void foreignSubmissionIs403() throws Exception {
        when(tutorService.explainFailure(any(), any())).thenThrow(new ForbiddenException("Access denied"));

        mvc.perform(post("/api/tutor/explain-failure").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"submissionId\":9}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptedSubmissionIs409() throws Exception {
        when(tutorService.explainFailure(any(), any())).thenThrow(new ConflictException("this one is ACCEPTED"));

        mvc.perform(post("/api/tutor/explain-failure").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"submissionId\":9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("this one is ACCEPTED"));
    }

    private ResultActions hint(String body) throws Exception {
        return mvc.perform(post("/api/tutor/hint").header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String bearer() {
        return "Bearer " + jwtService.issue(new AuthUser(1L, "alice", Role.USER));
    }
}
