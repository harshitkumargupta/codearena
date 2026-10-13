package com.codearena.api.tutor;

import static com.codearena.api.tutor.TutorFixtures.HIDDEN_EXPECTED;
import static com.codearena.api.tutor.TutorFixtures.HIDDEN_INPUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codearena.api.contest.ContestProblemRepository;
import com.codearena.api.error.ConflictException;
import com.codearena.api.error.ForbiddenException;
import com.codearena.api.error.NotFoundException;
import com.codearena.api.error.ValidationException;
import com.codearena.api.problem.ProblemRepository;
import com.codearena.api.problem.TestCase;
import com.codearena.api.problem.TestCaseRepository;
import com.codearena.api.security.AuthUser;
import com.codearena.api.submission.SubmissionRepository;
import com.codearena.api.tutor.dto.ExplainFailureRequest;
import com.codearena.api.tutor.dto.HintRequest;
import com.codearena.api.user.Role;
import com.codearena.common.Verdict;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TutorContextLoaderTest {

    private static final AuthUser OWNER = new AuthUser(1L, "alice", Role.USER);
    private static final AuthUser OTHER = new AuthUser(2L, "bob", Role.USER);
    private static final AuthUser ADMIN = new AuthUser(3L, "root", Role.ADMIN);

    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final TestCaseRepository testCaseRepository = mock(TestCaseRepository.class);
    private final ContestProblemRepository contestProblemRepository = mock(ContestProblemRepository.class);
    private final TutorContextLoader loader = new TutorContextLoader(problemRepository, submissionRepository,
            testCaseRepository, contestProblemRepository, Clock.fixed(TutorFixtures.NOW, ZoneOffset.UTC));

    @BeforeEach
    void problemOneWithOneSampleAndOneHiddenCase() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(TutorFixtures.problem(1L)));
        when(testCaseRepository.findByProblemIdAndSampleTrueOrderByOrdinalAsc(1L)).thenReturn(List.of(
                new TestCase(1L, "4 9\n2 7 11 15\n", "0 1", true, 1),
                new TestCase(1L, HIDDEN_INPUT, HIDDEN_EXPECTED, false, 2)));
    }

    @Test
    void hintWithoutSubmissionUsesOnlyTheProblem() {
        TutorContext context = loader.forHint(OWNER, new HintRequest(1L, 1, null));

        assertThat(context.submission()).isNull();
        assertThat(context.problemStatement()).contains("two numbers");
    }

    @Test
    void anotherUsersSubmissionIsForbidden() {
        stubSubmission(Verdict.WRONG_ANSWER, 1);

        assertThatThrownBy(() -> loader.forHint(OTHER, new HintRequest(1L, 2, 10L)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> loader.forExplanation(OTHER, new ExplainFailureRequest(10L)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminMayUseAnySubmission() {
        stubSubmission(Verdict.RUNTIME_ERROR, 1);

        assertThat(loader.forExplanation(ADMIN, new ExplainFailureRequest(10L)).submissionId()).isEqualTo(10L);
    }

    @Test
    void hintSubmissionMustBelongToTheProblem() {
        when(problemRepository.findById(2L)).thenReturn(Optional.of(TutorFixtures.problem(2L)));
        stubSubmission(Verdict.WRONG_ANSWER, 1);

        assertThatThrownBy(() -> loader.forHint(OWNER, new HintRequest(2L, 1, 10L)))
                .isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Verdict.class, names = {"ACCEPTED", "PENDING", "RUNNING", "INTERNAL_ERROR"})
    void explainFailureRejectsNonFailingSubmissions(Verdict status) {
        stubSubmission(status, null);

        assertThatThrownBy(() -> loader.forExplanation(OWNER, new ExplainFailureRequest(10L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("this one is " + status);
    }

    @Test
    void failingSampleOrdinalLoadsThatSample() {
        stubSubmission(Verdict.WRONG_ANSWER, 1);

        TutorContext context = loader.forExplanation(OWNER, new ExplainFailureRequest(10L));

        assertThat(context.failingSample()).isEqualTo(new TutorContext.SampleCase(1, "4 9\n2 7 11 15\n", "0 1"));
        assertThat(context.failedOnHiddenCase()).isFalse();
    }

    @Test
    void failingHiddenOrdinalNeverLoadsHiddenDataEvenIfTheQueryReturnedIt() {
        stubSubmission(Verdict.WRONG_ANSWER, 2);

        TutorContext context = loader.forExplanation(OWNER, new ExplainFailureRequest(10L));

        assertThat(context.failingSample()).isNull();
        assertThat(context.failedOnHiddenCase()).isTrue();
        assertThat(context.toString()).doesNotContain(HIDDEN_INPUT, HIDDEN_EXPECTED);
        verify(testCaseRepository, never()).findByProblemIdOrderByOrdinalAsc(anyLong());
    }

    @Test
    void tutorIsRefusedForAProblemInARunningContest() {
        when(contestProblemRepository.isInRunningContest(1L, TutorFixtures.NOW)).thenReturn(true);
        stubSubmission(Verdict.WRONG_ANSWER, 1);

        assertThatThrownBy(() -> loader.forHint(OWNER, new HintRequest(1L, 1, null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(TutorContextLoader.CONTEST_RUNNING);
        assertThatThrownBy(() -> loader.forExplanation(OWNER, new ExplainFailureRequest(10L)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(TutorContextLoader.CONTEST_RUNNING);
    }

    @Test
    void adminMayUseTheTutorDuringAContest() {
        when(contestProblemRepository.isInRunningContest(1L, TutorFixtures.NOW)).thenReturn(true);

        assertThat(loader.forHint(ADMIN, new HintRequest(1L, 1, null)).problemId()).isEqualTo(1L);
    }

    @Test
    void unpublishedOrMissingProblemIsNotFound() {
        assertThatThrownBy(() -> loader.forHint(OWNER, new HintRequest(99L, 1, null)))
                .isInstanceOf(NotFoundException.class);
    }

    private void stubSubmission(Verdict status, Integer failedOrdinal) {
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(
                TutorFixtures.submission(10L, OWNER.id(), 1L, status, failedOrdinal, "print(0)")));
    }
}
