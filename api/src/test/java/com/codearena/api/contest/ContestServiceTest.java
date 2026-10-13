package com.codearena.api.contest;

import static com.codearena.api.contest.ContestFixtures.END;
import static com.codearena.api.contest.ContestFixtures.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codearena.api.contest.dto.ContestDetail;
import com.codearena.api.contest.dto.JoinResponse;
import com.codearena.api.error.ConflictException;
import com.codearena.api.problem.Difficulty;
import com.codearena.api.problem.Problem;
import com.codearena.api.problem.ProblemRepository;
import com.codearena.api.security.AuthUser;
import com.codearena.api.user.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class ContestServiceTest {

    private static final AuthUser ALICE = new AuthUser(1L, "alice", Role.USER);

    private final ContestRepository contestRepository = mock(ContestRepository.class);
    private final ContestParticipantRepository participantRepository = mock(ContestParticipantRepository.class);
    private final ContestProblemRepository contestProblemRepository = mock(ContestProblemRepository.class);
    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    @BeforeEach
    void contestWithOneProblem() {
        when(contestRepository.findById(9L)).thenReturn(Optional.of(ContestFixtures.contest(9L)));
        when(contestProblemRepository.findByIdContestIdOrderByLabelAsc(9L))
                .thenReturn(List.of(new ContestProblem(9L, 5L, "A")));
        Problem problem = new Problem(null, START);
        problem.update("two-sum", "Two Sum", "s", Difficulty.EASY, 2000, 256, true);
        ReflectionTestUtils.setField(problem, "id", 5L);
        when(problemRepository.findAllById(List.of(5L))).thenReturn(List.of(problem));
    }

    @Test
    void problemsAreHiddenBeforeStart() {
        ContestDetail detail = serviceAt(START.minusSeconds(1)).get(9L);

        assertThat(detail.status()).isEqualTo(ContestStatus.UPCOMING);
        assertThat(detail.problemsVisible()).isFalse();
        assertThat(detail.problems()).isEmpty();
        verifyNoInteractions(contestProblemRepository);
    }

    @Test
    void problemsAreVisibleOnceStarted() {
        ContestDetail detail = serviceAt(START).get(9L);

        assertThat(detail.status()).isEqualTo(ContestStatus.RUNNING);
        assertThat(detail.problems()).singleElement()
                .satisfies(p -> assertThat(p.label() + ":" + p.slug()).isEqualTo("A:two-sum"));
    }

    @Test
    void joinBeforeOrDuringContestPublishesEventOnce() {
        when(participantRepository.joinIfAbsent(9L, 1L, START.minusSeconds(3600))).thenReturn(1);

        JoinResponse first = serviceAt(START.minusSeconds(3600)).join(9L, ALICE);

        assertThat(first.alreadyJoined()).isFalse();
        verify(eventPublisher).publishEvent(new ContestJoinedEvent(9L, 1L));
    }

    @Test
    void repeatedJoinIsIdempotent() {
        when(participantRepository.joinIfAbsent(9L, 1L, START)).thenReturn(0);

        JoinResponse again = serviceAt(START).join(9L, ALICE);

        assertThat(again.alreadyJoined()).isTrue();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void joinAfterEndIsRejected() {
        assertThatThrownBy(() -> serviceAt(END).join(9L, ALICE)).isInstanceOf(ConflictException.class);
        verify(participantRepository, never()).joinIfAbsent(anyLong(), anyLong(), any());
    }

    private ContestService serviceAt(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        return new ContestService(contestRepository, participantRepository,
                new ContestViews(contestProblemRepository, problemRepository, clock), eventPublisher, clock);
    }
}
