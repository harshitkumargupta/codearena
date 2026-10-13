package com.codearena.api.contest;

import static com.codearena.api.contest.ContestFixtures.END;
import static com.codearena.api.contest.ContestFixtures.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codearena.api.contest.dto.ContestProblemRequest;
import com.codearena.api.contest.dto.ContestRequest;
import com.codearena.api.error.ApiError;
import com.codearena.api.error.ValidationException;
import com.codearena.api.problem.Difficulty;
import com.codearena.api.problem.Problem;
import com.codearena.api.problem.ProblemRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AdminContestServiceTest {

    private final ContestRepository contestRepository = mock(ContestRepository.class);
    private final ContestProblemRepository contestProblemRepository = mock(ContestProblemRepository.class);
    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final AdminContestService service = new AdminContestService(contestRepository, contestProblemRepository,
            problemRepository, new ContestViews(contestProblemRepository, problemRepository,
            Clock.fixed(START.minusSeconds(60), ZoneOffset.UTC)));

    @Test
    void createsContestWithDefaultPenalty() {
        when(problemRepository.findAllById(Set.of(5L, 6L))).thenReturn(List.of(problem(5L, true), problem(6L, true)));
        when(contestRepository.save(any(Contest.class))).thenAnswer(inv -> {
            Contest c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 9L);
            return c;
        });

        service.create(request(null, problems("A", 5L, "B", 6L)), 1L);

        verify(contestRepository).save(org.mockito.ArgumentMatchers.argThat(c -> c.getPenaltyMinutes() == 20));
        verify(contestProblemRepository).saveAll(org.mockito.ArgumentMatchers.<List<ContestProblem>>argThat(l ->
                l.size() == 2 && l.get(0).getLabel().equals("A") && l.get(1).getProblemId() == 6L));
    }

    @Test
    void unpublishedProblemIsRejected() {
        when(problemRepository.findAllById(Set.of(5L, 6L))).thenReturn(List.of(problem(5L, true), problem(6L, false)));

        assertThatThrownBy(() -> service.create(request(20, problems("A", 5L, "B", 6L)), 1L))
                .isInstanceOfSatisfying(ValidationException.class, ex -> field(ex, "problems[1].problemId"));
        verify(contestRepository, never()).save(any());
    }

    @Test
    void missingProblemIsRejected() {
        when(problemRepository.findAllById(Set.of(99L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(request(20, problems("A", 99L)), 1L))
                .isInstanceOfSatisfying(ValidationException.class, ex -> field(ex, "problems[0].problemId"));
    }

    @Test
    void duplicateLabelsAndProblemsAreRejected() {
        assertThatThrownBy(() -> service.create(request(20, problems("A", 5L, "A", 6L)), 1L))
                .isInstanceOfSatisfying(ValidationException.class, ex -> field(ex, "problems"));
        assertThatThrownBy(() -> service.create(request(20, problems("A", 5L, "B", 5L)), 1L))
                .isInstanceOfSatisfying(ValidationException.class, ex -> field(ex, "problems"));
    }

    @Test
    void endMustBeAfterStartAndWithinMaxDuration() {
        assertThatThrownBy(() -> service.create(new ContestRequest("t", START, START, 20, problems("A", 5L)), 1L))
                .isInstanceOfSatisfying(ValidationException.class, ex -> field(ex, "endTime"));
        assertThatThrownBy(() -> service.create(new ContestRequest("t", START, START.plus(Duration.ofDays(31)), 20,
                problems("A", 5L)), 1L))
                .isInstanceOfSatisfying(ValidationException.class, ex -> field(ex, "endTime"));
    }

    private static ContestRequest request(Integer penalty, List<ContestProblemRequest> problems) {
        return new ContestRequest("Weekly #1", START, END, penalty, problems);
    }

    private static List<ContestProblemRequest> problems(Object... labelAndId) {
        List<ContestProblemRequest> list = new java.util.ArrayList<>();
        for (int i = 0; i < labelAndId.length; i += 2) {
            list.add(new ContestProblemRequest((Long) labelAndId[i + 1], (String) labelAndId[i]));
        }
        return list;
    }

    private static Problem problem(long id, boolean published) {
        Problem problem = new Problem(null, START);
        problem.update("p" + id, "P" + id, "s", Difficulty.EASY, 1000, 256, published);
        ReflectionTestUtils.setField(problem, "id", id);
        return problem;
    }

    private static void field(ValidationException ex, String name) {
        assertThat(ex.getFieldErrors()).extracting(ApiError.FieldError::field).containsExactly(name);
    }
}
