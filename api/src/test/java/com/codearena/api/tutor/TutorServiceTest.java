package com.codearena.api.tutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codearena.api.error.TooManyRequestsException;
import com.codearena.api.metrics.ApiMetrics;
import com.codearena.api.metrics.TutorOutcome;
import com.codearena.api.ratelimit.RateLimiter;
import com.codearena.api.security.AuthUser;
import com.codearena.api.tutor.dto.ExplainFailureRequest;
import com.codearena.api.tutor.dto.HintRequest;
import com.codearena.api.tutor.dto.TutorResponse;
import com.codearena.api.user.Role;
import com.codearena.common.Language;
import com.codearena.common.Verdict;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class TutorServiceTest {

    private static final AuthUser ALICE = new AuthUser(1L, "alice", Role.USER);
    private static final String LONG_BLOCK = "Solution:\n```python\n" + "x = 1\n".repeat(25) + "```";
    private static final String SHORT_BLOCK = "Like this:\n```\nseen = {}\n```";
    private static final String PROSE = "Think about storing each value's index so the complement can be found fast.";

    private final TutorContextLoader loader = mock(TutorContextLoader.class);
    private final TutorModel model = mock(TutorModel.class);
    private final TutorLimiter limiter = mock(TutorLimiter.class);
    private final TutorReservation reservation = mock(TutorReservation.class);
    private final HintLogRepository hintLogs = mock(HintLogRepository.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final TutorService service = new TutorService(loader,
            new TutorPromptBuilder(TutorFixtures.PROPERTIES, () -> "n0nce"), model, limiter, rateLimiter, hintLogs,
            TutorFixtures.PROPERTIES, new ApiMetrics(registry),
            Clock.fixed(TutorFixtures.NOW, ZoneOffset.UTC));

    @BeforeEach
    void contextAndReservation() {
        TutorContext context = new TutorContext(5L, "Two Sum", "statement",
                new TutorContext.SubmissionFacts(9L, Language.PYTHON, "print(0)", Verdict.WRONG_ANSWER, 3, null),
                null, true);
        when(loader.forHint(any(), any())).thenReturn(context);
        when(loader.forExplanation(any(), any())).thenReturn(context);
        when(limiter.reserve(anyLong(), anyLong(), anyBoolean())).thenReturn(reservation);
    }

    @Test
    void longCodeBlockAtLevelThreeTriggersOneStricterRetryThenFallback() {
        when(model.generate(any())).thenReturn(LONG_BLOCK, LONG_BLOCK);

        TutorResponse response = service.hint(ALICE, new HintRequest(5L, 3, 9L));

        ArgumentCaptor<TutorPrompt> prompts = ArgumentCaptor.forClass(TutorPrompt.class);
        verify(model, times(2)).generate(prompts.capture());
        assertThat(prompts.getAllValues().get(0).system()).doesNotContain("previous answer broke the code rule");
        assertThat(prompts.getAllValues().get(1).system()).contains("previous answer broke the code rule");
        assertThat(response.fallback()).isTrue();
        assertThat(response.text()).isEqualTo(TutorService.HINT_FALLBACK);
        verify(reservation, never()).commit();
        verify(reservation).close();
        verify(hintLogs, never()).save(any());
        verify(rateLimiter).checkTutor(1L);
    }

    @Test
    void perMinuteLimitIsCheckedBeforeAnyModelCallOrReservation() {
        doThrow(new TooManyRequestsException("Too many tutor requests.", 12))
                .when(rateLimiter).checkTutor(1L);

        assertThatThrownBy(() -> service.hint(ALICE, new HintRequest(5L, 1, null)))
                .isInstanceOf(TooManyRequestsException.class);
        verify(model, never()).generate(any());
        verify(limiter, never()).reserve(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void retryThatComesBackCleanIsDelivered() {
        when(model.generate(any())).thenReturn(LONG_BLOCK, PROSE);

        TutorResponse response = service.hint(ALICE, new HintRequest(5L, 3, 9L));

        assertThat(response.fallback()).isFalse();
        assertThat(response.text()).isEqualTo(PROSE);
        verify(model, times(2)).generate(any());
        verify(reservation).commit();
    }

    @Test
    void shortCodeBlockIsAllowedAtLevelThree() {
        when(model.generate(any())).thenReturn(SHORT_BLOCK);

        assertThat(service.hint(ALICE, new HintRequest(5L, 3, null)).text()).isEqualTo(SHORT_BLOCK);
        verify(model, times(1)).generate(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void anyCodeBlockIsRejectedAtLevelsOneAndTwo(int level) {
        when(model.generate(any())).thenReturn(SHORT_BLOCK, SHORT_BLOCK);

        TutorResponse response = service.hint(ALICE, new HintRequest(5L, level, null));

        assertThat(response.fallback()).isTrue();
        verify(model, times(2)).generate(any());
        verify(hintLogs, never()).save(any());
    }

    @Test
    void deliveredHintIsLoggedAndConsumesLimits() {
        when(model.generate(any())).thenReturn(PROSE);

        TutorResponse response = service.hint(ALICE, new HintRequest(5L, 2, 9L));

        assertThat(response).isEqualTo(new TutorResponse(HintKind.HINT, 2, PROSE, false));
        verify(limiter).reserve(1L, 5L, false);
        verify(reservation).commit();
        ArgumentCaptor<HintLog> log = ArgumentCaptor.forClass(HintLog.class);
        verify(hintLogs).save(log.capture());
        assertThat(List.of(log.getValue().getUserId(), log.getValue().getProblemId(), log.getValue().getSubmissionId()))
                .containsExactly(1L, 5L, 9L);
        assertThat(log.getValue().getKind()).isEqualTo(HintKind.HINT);
        assertThat(log.getValue().getLevel()).isEqualTo(2);
    }

    @Test
    void levelThreeReservesTheCooldown() {
        when(model.generate(any())).thenReturn(PROSE);

        service.hint(ALICE, new HintRequest(5L, 3, null));

        verify(limiter).reserve(1L, 5L, true);
    }

    @Test
    void explanationIsLoggedWithoutLevelAndAllowsShortQuotes() {
        when(model.generate(any())).thenReturn(SHORT_BLOCK);

        TutorResponse response = service.explainFailure(ALICE, new ExplainFailureRequest(9L));

        assertThat(response.kind()).isEqualTo(HintKind.EXPLANATION);
        assertThat(response.level()).isNull();
        ArgumentCaptor<HintLog> log = ArgumentCaptor.forClass(HintLog.class);
        verify(hintLogs).save(log.capture());
        assertThat(log.getValue().getKind()).isEqualTo(HintKind.EXPLANATION);
        assertThat(log.getValue().getLevel()).isNull();
        verify(limiter).reserve(1L, 5L, false);
    }

    @Test
    void modelFailureIsUnavailableAndConsumesNothing() {
        when(model.generate(any())).thenThrow(new TutorUnavailableException());

        assertThatThrownBy(() -> service.hint(ALICE, new HintRequest(5L, 3, null)))
                .isInstanceOf(TutorUnavailableException.class)
                .hasMessage(TutorUnavailableException.MESSAGE);
        verify(reservation, never()).commit();
        verify(reservation).close();
        verify(hintLogs, never()).save(any());
    }

    @Test
    void eachCallIsCountedOnceUnderItsOutcome() {
        when(model.generate(any())).thenReturn(PROSE);
        service.hint(ALICE, new HintRequest(5L, 1, null));

        when(model.generate(any())).thenReturn(LONG_BLOCK);
        service.hint(ALICE, new HintRequest(5L, 1, null));

        when(model.generate(any())).thenThrow(new TutorUnavailableException());
        assertThatThrownBy(() -> service.hint(ALICE, new HintRequest(5L, 1, null)))
                .isInstanceOf(TutorUnavailableException.class);

        doThrow(new TooManyRequestsException("Too many tutor requests.", 12)).when(rateLimiter).checkTutor(1L);
        assertThatThrownBy(() -> service.explainFailure(ALICE, new ExplainFailureRequest(9L)))
                .isInstanceOf(TooManyRequestsException.class);

        for (TutorOutcome outcome : TutorOutcome.values()) {
            assertThat(registry.get(ApiMetrics.TUTOR_CALLS)
                    .tag("outcome", outcome.name().toLowerCase(Locale.ROOT)).counter().count())
                    .as(outcome.name())
                    .isEqualTo(1.0);
        }
    }
}
