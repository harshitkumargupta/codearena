package com.codearena.api.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codearena.api.error.ApiError;
import com.codearena.api.error.ValidationException;
import com.codearena.api.problem.dto.AddTestCasesRequest;
import com.codearena.api.problem.dto.TestCaseRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AdminProblemServiceTest {

    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final TestCaseRepository testCaseRepository = mock(TestCaseRepository.class);
    private final AdminProblemService service = new AdminProblemService(problemRepository, testCaseRepository,
            new ProblemLimitsProperties(1000), Clock.systemUTC());

    @BeforeEach
    void problemExists() {
        when(problemRepository.findById(3L)).thenReturn(Optional.of(new Problem(1L, Instant.now())));
    }

    @Test
    void uploadThatWouldExceedTheTotalTestDataCapIsRejected() {
        when(testCaseRepository.totalTestDataBytes(3L)).thenReturn(900L);

        assertThatThrownBy(() -> service.addTestCases(3L, new AddTestCasesRequest(List.of(
                new TestCaseRequest("x".repeat(60), "y".repeat(60), false)))))
                .isInstanceOfSatisfying(ValidationException.class, ex -> assertThat(ex.getFieldErrors())
                        .extracting(ApiError.FieldError::field).containsExactly("testCases"));
        verify(testCaseRepository, never()).saveAll(anyList());
    }

    @Test
    void uploadWithinTheCapIsSaved() {
        when(testCaseRepository.totalTestDataBytes(3L)).thenReturn(800L);
        when(testCaseRepository.findMaxOrdinal(3L)).thenReturn(2);
        when(testCaseRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<TestCase> saved = inv.getArgument(0);
            saved.forEach(tc -> ReflectionTestUtils.setField(tc, "id", 11L));
            return saved;
        });

        assertThat(service.addTestCases(3L, new AddTestCasesRequest(List.of(
                new TestCaseRequest("x".repeat(90), "y".repeat(90), true))))).hasSize(1);
    }
}
