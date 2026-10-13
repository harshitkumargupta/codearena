package com.codearena.api.contest;

import static com.codearena.api.contest.ContestFixtures.END;
import static com.codearena.api.contest.ContestFixtures.START;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codearena.api.error.ApiError;
import com.codearena.api.error.ForbiddenException;
import com.codearena.api.error.ValidationException;
import com.codearena.api.security.AuthUser;
import com.codearena.api.user.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContestSubmissionValidatorTest {

    private static final AuthUser ALICE = new AuthUser(1L, "alice", Role.USER);

    private final ContestRepository contestRepository = mock(ContestRepository.class);
    private final ContestParticipantRepository participantRepository = mock(ContestParticipantRepository.class);
    private final ContestProblemRepository contestProblemRepository = mock(ContestProblemRepository.class);

    @BeforeEach
    void contestWithAliceAndProblemFive() {
        when(contestRepository.findById(9L)).thenReturn(Optional.of(ContestFixtures.contest(9L)));
        when(participantRepository.existsById(new ContestParticipantId(9L, 1L))).thenReturn(true);
        when(contestProblemRepository.existsById(new ContestProblemId(9L, 5L))).thenReturn(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-13T10:00:00Z", "2026-10-13T11:00:00Z", "2026-10-13T11:59:59.999Z"})
    void participantInsideWindowOnContestProblemIsAccepted(String now) {
        assertThatCode(() -> validatorAt(Instant.parse(now)).validate(ALICE, 9L, 5L)).doesNotThrowAnyException();
    }

    @Test
    void unknownContestIsRejected() {
        when(contestRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validatorAt(START).validate(ALICE, 9L, 5L))
                .isInstanceOfSatisfying(ValidationException.class, ex -> fieldIs(ex, "contestId"));
    }

    @Test
    void nonParticipantIsForbidden() {
        AuthUser bob = new AuthUser(2L, "bob", Role.USER);

        assertThatThrownBy(() -> validatorAt(START).validate(bob, 9L, 5L)).isInstanceOf(ForbiddenException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-13T09:59:59Z", "2026-10-13T12:00:00Z", "2026-10-14T00:00:00Z"})
    void submissionOutsideWindowIsRejected(String now) {
        assertThatThrownBy(() -> validatorAt(Instant.parse(now)).validate(ALICE, 9L, 5L))
                .isInstanceOfSatisfying(ValidationException.class, ex -> fieldIs(ex, "contestId"));
    }

    @Test
    void problemOutsideContestIsRejected() {
        assertThatThrownBy(() -> validatorAt(START.plusSeconds(60)).validate(ALICE, 9L, 6L))
                .isInstanceOfSatisfying(ValidationException.class, ex -> fieldIs(ex, "problemId"));
    }

    @Test
    void windowEndIsExclusive() {
        assertThatThrownBy(() -> validatorAt(END).validate(ALICE, 9L, 5L)).isInstanceOf(ValidationException.class);
    }

    private ContestSubmissionValidator validatorAt(Instant now) {
        return new ContestSubmissionValidator(contestRepository, participantRepository, contestProblemRepository,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static void fieldIs(ValidationException ex, String field) {
        org.assertj.core.api.Assertions.assertThat(ex.getFieldErrors()).extracting(ApiError.FieldError::field)
                .containsExactly(field);
    }
}
