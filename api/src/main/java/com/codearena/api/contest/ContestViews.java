package com.codearena.api.contest;

import com.codearena.api.contest.dto.ContestDetail;
import com.codearena.api.contest.dto.ContestProblemView;
import com.codearena.api.contest.dto.ContestSummary;
import com.codearena.api.problem.Problem;
import com.codearena.api.problem.ProblemRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ContestViews {

    private final ContestProblemRepository contestProblemRepository;
    private final ProblemRepository problemRepository;
    private final Clock clock;

    public ContestViews(ContestProblemRepository contestProblemRepository, ProblemRepository problemRepository,
                        Clock clock) {
        this.contestProblemRepository = contestProblemRepository;
        this.problemRepository = problemRepository;
        this.clock = clock;
    }

    public ContestSummary summary(Contest contest) {
        return new ContestSummary(contest.getId(), contest.getTitle(), contest.getStartTime(), contest.getEndTime(),
                contest.getPenaltyMinutes(), contest.statusAt(clock.instant()));
    }

    public ContestDetail detail(Contest contest, boolean revealProblemsEarly) {
        Instant now = clock.instant();
        ContestStatus status = contest.statusAt(now);
        boolean visible = revealProblemsEarly || status != ContestStatus.UPCOMING;
        return new ContestDetail(contest.getId(), contest.getTitle(), contest.getStartTime(), contest.getEndTime(),
                contest.getPenaltyMinutes(), status, visible, visible ? problems(contest.getId()) : List.of());
    }

    private List<ContestProblemView> problems(long contestId) {
        List<ContestProblem> entries = contestProblemRepository.findByIdContestIdOrderByLabelAsc(contestId);
        Map<Long, Problem> problems = problemRepository
                .findAllById(entries.stream().map(ContestProblem::getProblemId).toList()).stream()
                .collect(Collectors.toMap(Problem::getId, Function.identity()));
        return entries.stream()
                .filter(e -> problems.containsKey(e.getProblemId()))
                .map(e -> {
                    Problem p = problems.get(e.getProblemId());
                    return new ContestProblemView(e.getLabel(), p.getId(), p.getSlug(), p.getTitle(), p.getDifficulty());
                })
                .toList();
    }
}
