package com.codearena.api.contest.leaderboard;

import com.codearena.api.contest.Contest;
import com.codearena.api.contest.ContestParticipantRepository;
import com.codearena.api.contest.ContestRepository;
import com.codearena.api.contest.dto.LeaderboardEntry;
import com.codearena.api.contest.dto.LeaderboardResponse;
import com.codearena.api.contest.scoring.ScoreCalculator;
import com.codearena.api.contest.scoring.ScoredSubmission;
import com.codearena.api.contest.scoring.UserScore;
import com.codearena.api.error.NotFoundException;
import com.codearena.api.submission.SubmissionRepository;
import com.codearena.api.user.User;
import com.codearena.api.user.UserRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redis sorted set {@code contest:{id}:lb} (member = userId, score = {@link UserScore#encode()}). Redis holds
 * derived data only: every score is recomputed from the database, and a missing key is rebuilt.
 */
@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

    private final StringRedisTemplate redis;
    private final ContestRepository contestRepository;
    private final ContestParticipantRepository participantRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final LeaderboardProperties properties;

    public LeaderboardService(StringRedisTemplate redis, ContestRepository contestRepository,
                              ContestParticipantRepository participantRepository,
                              SubmissionRepository submissionRepository, UserRepository userRepository,
                              LeaderboardProperties properties) {
        this.redis = redis;
        this.contestRepository = contestRepository;
        this.participantRepository = participantRepository;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    public static String key(long contestId) {
        return "contest:" + contestId + ":lb";
    }

    @Transactional(readOnly = true)
    public LeaderboardResponse top(long contestId, Integer limit) {
        Contest contest = find(contestId);
        int n = Math.clamp(limit == null ? properties.defaultLimit() : limit, 1, properties.maxLimit());
        List<Map.Entry<Long, UserScore>> ranked;
        try {
            if (!Boolean.TRUE.equals(redis.hasKey(key(contestId)))) {
                rebuild(contest);
            }
            Set<TypedTuple<String>> tuples = redis.opsForZSet().reverseRangeWithScores(key(contestId), 0, n - 1L);
            ranked = new ArrayList<>();
            for (TypedTuple<String> tuple : tuples == null ? Set.<TypedTuple<String>>of() : tuples) {
                ranked.add(Map.entry(Long.valueOf(tuple.getValue()), UserScore.decode(tuple.getScore().longValue())));
            }
        } catch (DataAccessException ex) {
            log.warn("Redis unavailable for contest {} leaderboard; computing it from the database: {}", contestId,
                    ex.toString());
            ranked = computeAll(contest).entrySet().stream()
                    .sorted(Map.Entry.<Long, UserScore>comparingByValue(
                            Comparator.comparingLong(UserScore::encode)).reversed())
                    .limit(n)
                    .toList();
        }
        return new LeaderboardResponse(contestId, rank(ranked));
    }

    @Transactional(readOnly = true)
    public void rebuild(long contestId) {
        rebuild(find(contestId));
    }

    /**
     * @return the contest whose leaderboard changed, or empty if the submission is not a contest submission
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Long> refreshForSubmission(long submissionId) {
        return submissionRepository.findById(submissionId)
                .filter(s -> s.getContestId() != null)
                .map(s -> {
                    updateUser(find(s.getContestId()), s.getUserId());
                    return s.getContestId();
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void recordJoin(long contestId, long userId) {
        if (Boolean.TRUE.equals(redis.hasKey(key(contestId)))) {
            redis.opsForZSet().addIfAbsent(key(contestId), Long.toString(userId), 0);
        } else {
            rebuild(find(contestId));
        }
    }

    private void updateUser(Contest contest, long userId) {
        if (!Boolean.TRUE.equals(redis.hasKey(key(contest.getId())))) {
            rebuild(contest);
            return;
        }
        UserScore score = ScoreCalculator.score(contest.window(), contest.getPenaltyMinutes(),
                submissionRepository.findScoredSubmissions(contest.getId(), userId));
        redis.opsForZSet().add(key(contest.getId()), Long.toString(userId), score.encode());
    }

    private void rebuild(Contest contest) {
        Map<Long, UserScore> scores = computeAll(contest);
        String key = key(contest.getId());
        if (scores.isEmpty()) {
            redis.delete(key);
            return;
        }
        // Built under a temporary key and renamed so readers never see a half-built leaderboard.
        String staging = key + ":rebuild:" + UUID.randomUUID();
        Set<TypedTuple<String>> tuples = scores.entrySet().stream()
                .map(e -> (TypedTuple<String>) new DefaultTypedTuple<>(e.getKey().toString(),
                        (double) e.getValue().encode()))
                .collect(Collectors.toSet());
        redis.opsForZSet().add(staging, tuples);
        redis.rename(staging, key);
        log.info("Rebuilt leaderboard for contest {} ({} participants)", contest.getId(), scores.size());
    }

    private Map<Long, UserScore> computeAll(Contest contest) {
        Map<Long, List<ScoredSubmission>> byUser = submissionRepository.findScoredSubmissions(contest.getId()).stream()
                .collect(Collectors.groupingBy(ScoredSubmission::userId));
        Map<Long, UserScore> scores = new HashMap<>();
        for (Long userId : participantRepository.findUserIds(contest.getId())) {
            scores.put(userId, ScoreCalculator.score(contest.window(), contest.getPenaltyMinutes(),
                    byUser.getOrDefault(userId, List.of())));
        }
        return scores;
    }

    private List<LeaderboardEntry> rank(List<Map.Entry<Long, UserScore>> byScoreDescending) {
        Map<Long, String> usernames = userRepository.findAllById(
                        byScoreDescending.stream().map(Map.Entry::getKey).toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        Function<Map.Entry<Long, UserScore>, String> username = e -> usernames.getOrDefault(e.getKey(), "");
        List<Map.Entry<Long, UserScore>> ordered = byScoreDescending.stream()
                .sorted(Comparator.<Map.Entry<Long, UserScore>>comparingLong(e -> -e.getValue().encode())
                        .thenComparing(username))
                .toList();
        List<LeaderboardEntry> entries = new ArrayList<>();
        int rank = 0;
        for (int i = 0; i < ordered.size(); i++) {
            UserScore score = ordered.get(i).getValue();
            if (i == 0 || score.encode() != ordered.get(i - 1).getValue().encode()) {
                rank = i + 1;
            }
            entries.add(new LeaderboardEntry(rank, ordered.get(i).getKey(), username.apply(ordered.get(i)),
                    score.solved(), score.penaltyMinutes()));
        }
        return entries;
    }

    private Contest find(long contestId) {
        return contestRepository.findById(contestId).orElseThrow(() -> new NotFoundException("Contest not found"));
    }
}
