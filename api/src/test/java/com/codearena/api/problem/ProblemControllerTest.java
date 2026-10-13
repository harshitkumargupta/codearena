package com.codearena.api.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codearena.api.support.WebSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProblemController.class)
@Import({WebSecurityTestConfig.class, ProblemService.class})
@ActiveProfiles("test")
class ProblemControllerTest {

    private static final String HIDDEN_MARKER = "HIDDEN_7f3a";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProblemRepository problemRepository;

    @MockitoBean
    private TestCaseRepository testCaseRepository;

    @Test
    void problemDetailNeverContainsHiddenTestCases() throws Exception {
        Problem problem = problem(1L, "two-sum");
        when(problemRepository.findBySlugAndPublishedTrue("two-sum")).thenReturn(Optional.of(problem));
        // The repository is made to return a hidden case too, simulating a faulty query.
        when(testCaseRepository.findByProblemIdAndSampleTrueOrderByOrdinalAsc(1L)).thenReturn(List.of(
                new TestCase(1L, "4 9\n2 7 11 15\n", "0 1", true, 1),
                new TestCase(1L, HIDDEN_MARKER + "_input", HIDDEN_MARKER + "_output", false, 2)));

        String body = mvc.perform(get("/api/problems/two-sum"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.samples.length()").value(1))
                .andExpect(jsonPath("$.samples[0].input").value("4 9\n2 7 11 15\n"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(HIDDEN_MARKER);
    }

    @Test
    void problemListNeverTouchesTestCases() throws Exception {
        when(problemRepository.findByPublishedTrue(any())).thenReturn(
                new PageImpl<>(List.of(problem(1L, "two-sum")), PageRequest.of(0, 20), 1));

        String body = mvc.perform(get("/api/problems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug").value("two-sum"))
                .andExpect(jsonPath("$.content[0].samples").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(HIDDEN_MARKER);
        verify(testCaseRepository, never()).findByProblemIdAndSampleTrueOrderByOrdinalAsc(anyLong());
    }

    @Test
    void listFiltersByDifficultyAndCapsPageSize() throws Exception {
        when(problemRepository.findByPublishedTrueAndDifficulty(any(), any())).thenReturn(
                new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        mvc.perform(get("/api/problems").param("difficulty", "HARD").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void invalidDifficultyReturns400() throws Exception {
        mvc.perform(get("/api/problems").param("difficulty", "IMPOSSIBLE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'difficulty'"));
    }

    @Test
    void unknownSlugReturns404Json() throws Exception {
        when(problemRepository.findBySlugAndPublishedTrue("nope")).thenReturn(Optional.empty());

        mvc.perform(get("/api/problems/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Problem not found"));
    }

    private static Problem problem(long id, String slug) {
        Problem problem = new Problem(null, Instant.parse("2026-10-13T10:00:00Z"));
        problem.update(slug, "Two Sum", "Find two numbers.", Difficulty.EASY, 2000, 256, true);
        ReflectionTestUtils.setField(problem, "id", id);
        return problem;
    }
}
