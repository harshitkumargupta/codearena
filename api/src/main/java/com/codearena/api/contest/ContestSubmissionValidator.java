package com.codearena.api.contest;

import com.codearena.api.error.ForbiddenException;
import com.codearena.api.error.ValidationException;
import com.codearena.api.security.AuthUser;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class ContestSubmissionValidator {

    private final ContestRepository contestRepository;
    private final ContestParticipantRepository participantRepository;
    private final ContestProblemRepository contestProblemRepository;
    private final Clock clock;

    public ContestSubmissionValidator(ContestRepository contestRepository,
                                      ContestParticipantRepository participantRepository,
                                      ContestProblemRepository contestProblemRepository, Clock clock) {
        this.contestRepository = contestRepository;
        this.participantRepository = participantRepository;
        this.contestProblemRepository = contestProblemRepository;
        this.clock = clock;
    }

    public void validate(AuthUser user, long contestId, long problemId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new ValidationException("contestId", "contest does not exist"));
        if (!participantRepository.existsById(new ContestParticipantId(contestId, user.id()))) {
            throw new ForbiddenException("You have not joined this contest");
        }
        if (contest.statusAt(clock.instant()) != ContestStatus.RUNNING) {
            throw new ValidationException("contestId", "contest is not running");
        }
        if (!contestProblemRepository.existsById(new ContestProblemId(contestId, problemId))) {
            throw new ValidationException("problemId", "problem is not part of this contest");
        }
    }
}
