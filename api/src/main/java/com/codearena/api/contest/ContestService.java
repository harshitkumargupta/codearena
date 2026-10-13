package com.codearena.api.contest;

import com.codearena.api.contest.dto.ContestDetail;
import com.codearena.api.contest.dto.ContestSummary;
import com.codearena.api.contest.dto.JoinResponse;
import com.codearena.api.error.ConflictException;
import com.codearena.api.error.NotFoundException;
import com.codearena.api.security.AuthUser;
import com.codearena.api.web.PageRequests;
import com.codearena.api.web.PageResponse;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContestService {

    private final ContestRepository contestRepository;
    private final ContestParticipantRepository participantRepository;
    private final ContestViews views;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ContestService(ContestRepository contestRepository, ContestParticipantRepository participantRepository,
                          ContestViews views, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.contestRepository = contestRepository;
        this.participantRepository = participantRepository;
        this.views = views;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ContestSummary> list(int page, int size) {
        return PageResponse.of(contestRepository.findAll(
                PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"))), views::summary);
    }

    @Transactional(readOnly = true)
    public ContestDetail get(long id) {
        return views.detail(find(id), false);
    }

    @Transactional
    public JoinResponse join(long contestId, AuthUser user) {
        Contest contest = find(contestId);
        Instant now = clock.instant();
        if (contest.statusAt(now) == ContestStatus.ENDED) {
            throw new ConflictException("Contest has ended");
        }
        boolean joined = participantRepository.joinIfAbsent(contestId, user.id(), now) == 1;
        if (joined) {
            eventPublisher.publishEvent(new ContestJoinedEvent(contestId, user.id()));
        }
        return new JoinResponse(contestId, user.id(), !joined);
    }

    private Contest find(long id) {
        return contestRepository.findById(id).orElseThrow(() -> new NotFoundException("Contest not found"));
    }
}
