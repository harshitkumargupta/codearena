package com.codearena.api.problem;

import com.codearena.api.error.ConflictException;
import com.codearena.api.error.NotFoundException;
import com.codearena.api.problem.dto.AddTestCasesRequest;
import com.codearena.api.problem.dto.AdminProblemResponse;
import com.codearena.api.problem.dto.ProblemRequest;
import com.codearena.api.problem.dto.TestCaseRequest;
import com.codearena.api.problem.dto.TestCaseSummary;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminProblemService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final Clock clock;

    public AdminProblemService(ProblemRepository problemRepository, TestCaseRepository testCaseRepository,
                               Clock clock) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
        this.clock = clock;
    }

    @Transactional
    public AdminProblemResponse create(ProblemRequest request, long adminId) {
        if (problemRepository.existsBySlug(request.slug())) {
            throw new ConflictException("Slug is already in use");
        }
        Problem problem = new Problem(adminId, clock.instant());
        apply(problem, request);
        return AdminProblemResponse.from(problemRepository.save(problem));
    }

    @Transactional
    public AdminProblemResponse update(long id, ProblemRequest request) {
        Problem problem = find(id);
        if (!problem.getSlug().equals(request.slug()) && problemRepository.existsBySlug(request.slug())) {
            throw new ConflictException("Slug is already in use");
        }
        apply(problem, request);
        return AdminProblemResponse.from(problem);
    }

    @Transactional
    public List<TestCaseSummary> addTestCases(long problemId, AddTestCasesRequest request) {
        find(problemId);
        int ordinal = testCaseRepository.findMaxOrdinal(problemId);
        List<TestCase> testCases = new ArrayList<>();
        for (TestCaseRequest tc : request.testCases()) {
            testCases.add(new TestCase(problemId, tc.input(), tc.expectedOutput(), tc.sample(), ++ordinal));
        }
        return testCaseRepository.saveAll(testCases).stream().map(TestCaseSummary::from).toList();
    }

    private Problem find(long id) {
        return problemRepository.findById(id).orElseThrow(() -> new NotFoundException("Problem not found"));
    }

    private static void apply(Problem problem, ProblemRequest request) {
        problem.update(request.slug(), request.title(), request.statementMd(), request.difficulty(),
                request.timeLimitMs(), request.memoryLimitMb(), request.published());
    }
}
