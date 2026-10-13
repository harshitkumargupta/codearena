package com.codearena.api.contest;

import com.codearena.api.contest.dto.ContestDetail;
import com.codearena.api.contest.dto.ContestProblemRequest;
import com.codearena.api.contest.dto.ContestRequest;
import com.codearena.api.error.ValidationException;
import com.codearena.api.problem.Problem;
import com.codearena.api.problem.ProblemRepository;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminContestService {

    static final int DEFAULT_PENALTY_MINUTES = 20;
    static final Duration MAX_DURATION = Duration.ofDays(30);

    private final ContestRepository contestRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final ProblemRepository problemRepository;
    private final ContestViews views;

    public AdminContestService(ContestRepository contestRepository, ContestProblemRepository contestProblemRepository,
                               ProblemRepository problemRepository, ContestViews views) {
        this.contestRepository = contestRepository;
        this.contestProblemRepository = contestProblemRepository;
        this.problemRepository = problemRepository;
        this.views = views;
    }

    @Transactional
    public ContestDetail create(ContestRequest request, long adminId) {
        validateWindow(request);
        validateProblems(request.problems());
        int penalty = request.penaltyMinutes() == null ? DEFAULT_PENALTY_MINUTES : request.penaltyMinutes();
        Contest contest = contestRepository.save(new Contest(request.title(), request.startTime(), request.endTime(),
                penalty, adminId));
        contestProblemRepository.saveAll(request.problems().stream()
                .map(p -> new ContestProblem(contest.getId(), p.problemId(), p.label()))
                .toList());
        return views.detail(contest, true);
    }

    private static void validateWindow(ContestRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new ValidationException("endTime", "must be after startTime");
        }
        if (Duration.between(request.startTime(), request.endTime()).compareTo(MAX_DURATION) > 0) {
            throw new ValidationException("endTime", "contest may last at most " + MAX_DURATION.toDays() + " days");
        }
    }

    private void validateProblems(List<ContestProblemRequest> requested) {
        Set<String> labels = new HashSet<>();
        Set<Long> ids = new HashSet<>();
        for (ContestProblemRequest p : requested) {
            if (!labels.add(p.label())) {
                throw new ValidationException("problems", "labels must be unique");
            }
            if (!ids.add(p.problemId())) {
                throw new ValidationException("problems", "a problem can appear only once");
            }
        }
        Map<Long, Problem> found = problemRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Problem::getId, Function.identity()));
        for (int i = 0; i < requested.size(); i++) {
            Problem problem = found.get(requested.get(i).problemId());
            if (problem == null || !problem.isPublished()) {
                throw new ValidationException("problems[" + i + "].problemId", "problem does not exist or is not published");
            }
        }
    }
}
