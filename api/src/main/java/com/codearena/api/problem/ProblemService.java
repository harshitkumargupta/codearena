package com.codearena.api.problem;

import com.codearena.api.error.NotFoundException;
import com.codearena.api.problem.dto.ProblemDetail;
import com.codearena.api.problem.dto.ProblemSummary;
import com.codearena.api.problem.dto.SampleTestCase;
import com.codearena.api.web.PageRequests;
import com.codearena.api.web.PageResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

    public ProblemService(ProblemRepository problemRepository, TestCaseRepository testCaseRepository) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProblemSummary> list(Difficulty difficulty, int page, int size) {
        PageRequest pageable = PageRequests.of(page, size, Sort.by("id"));
        Page<Problem> result = difficulty == null
                ? problemRepository.findByPublishedTrue(pageable)
                : problemRepository.findByPublishedTrueAndDifficulty(difficulty, pageable);
        return PageResponse.of(result, ProblemSummary::from);
    }

    @Transactional(readOnly = true)
    public ProblemDetail getPublished(String slug) {
        Problem problem = problemRepository.findBySlugAndPublishedTrue(slug)
                .orElseThrow(() -> new NotFoundException("Problem not found"));
        // Filtered again here so a wrong query can never leak hidden cases into the response.
        List<SampleTestCase> samples = testCaseRepository.findByProblemIdAndSampleTrueOrderByOrdinalAsc(problem.getId())
                .stream()
                .filter(TestCase::isSample)
                .map(t -> new SampleTestCase(t.getOrdinal(), t.getInput(), t.getExpectedOutput()))
                .toList();
        return new ProblemDetail(problem.getId(), problem.getSlug(), problem.getTitle(), problem.getStatementMd(),
                problem.getDifficulty(), problem.getTimeLimitMs(), problem.getMemoryLimitMb(), samples);
    }
}
