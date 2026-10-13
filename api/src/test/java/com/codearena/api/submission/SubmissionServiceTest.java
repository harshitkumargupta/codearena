package com.codearena.api.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codearena.api.error.ForbiddenException;
import com.codearena.api.error.NotFoundException;
import com.codearena.api.error.ValidationException;
import com.codearena.api.judging.SubmissionCreatedEvent;
import com.codearena.api.problem.Difficulty;
import com.codearena.api.problem.Problem;
import com.codearena.api.problem.ProblemRepository;
import com.codearena.api.security.AuthUser;
import com.codearena.api.submission.dto.SubmitRequest;
import com.codearena.api.submission.dto.SubmitResponse;
import com.codearena.api.user.Role;
import com.codearena.common.Language;
import com.codearena.common.Verdict;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class SubmissionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");
    private static final AuthUser OWNER = new AuthUser(1L, "alice", Role.USER);
    private static final AuthUser OTHER = new AuthUser(2L, "bob", Role.USER);
    private static final AuthUser ADMIN = new AuthUser(3L, "root", Role.ADMIN);

    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SubmissionService service = new SubmissionService(submissionRepository, problemRepository,
            eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void submitSavesPendingRowForCaller() {
        when(problemRepository.findById(5L)).thenReturn(Optional.of(problem(5L, true)));
        when(submissionRepository.save(any(Submission.class)))
                .thenAnswer(inv -> withId(inv.getArgument(0), 100L));

        SubmitResponse response = service.submit(OWNER, new SubmitRequest(5L, Language.JAVA, "class Main {}"));

        ArgumentCaptor<Submission> saved = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(saved.capture());
        assertThat(response).isEqualTo(new SubmitResponse(100L, Verdict.PENDING));
        assertThat(saved.getValue().getUserId()).isEqualTo(1L);
        assertThat(saved.getValue().getStatus()).isEqualTo(Verdict.PENDING);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        verify(eventPublisher).publishEvent(new SubmissionCreatedEvent(100L));
    }

    @Test
    void submitToUnknownProblemIsRejected() {
        when(problemRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(OWNER, new SubmitRequest(5L, Language.JAVA, "x")))
                .isInstanceOf(ValidationException.class);
        verify(submissionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void submitToUnpublishedProblemIsRejected() {
        when(problemRepository.findById(5L)).thenReturn(Optional.of(problem(5L, false)));

        assertThatThrownBy(() -> service.submit(OWNER, new SubmitRequest(5L, Language.PYTHON, "x")))
                .isInstanceOf(ValidationException.class);
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void ownerCanReadSubmission() {
        when(submissionRepository.findById(100L)).thenReturn(Optional.of(storedSubmission()));

        assertThat(service.get(OWNER, 100L).sourceCode()).isEqualTo("print(1)");
    }

    @Test
    void adminCanReadAnySubmission() {
        when(submissionRepository.findById(100L)).thenReturn(Optional.of(storedSubmission()));

        assertThat(service.get(ADMIN, 100L).id()).isEqualTo(100L);
    }

    @Test
    void otherUserIsForbidden() {
        when(submissionRepository.findById(100L)).thenReturn(Optional.of(storedSubmission()));

        assertThatThrownBy(() -> service.get(OTHER, 100L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void missingSubmissionIsNotFound() {
        when(submissionRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(OWNER, 100L)).isInstanceOf(NotFoundException.class);
    }

    private static Submission storedSubmission() {
        return withId(new Submission(OWNER.id(), 5L, Language.PYTHON, "print(1)", NOW), 100L);
    }

    private static Problem problem(long id, boolean published) {
        Problem problem = new Problem(null, NOW);
        problem.update("p" + id, "P", "S", Difficulty.EASY, 1000, 256, published);
        ReflectionTestUtils.setField(problem, "id", id);
        return problem;
    }

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
