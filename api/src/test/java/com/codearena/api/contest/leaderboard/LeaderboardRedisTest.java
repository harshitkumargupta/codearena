package com.codearena.api.contest.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codearena.api.contest.Contest;
import com.codearena.api.contest.ContestParticipantRepository;
import com.codearena.api.contest.ContestRepository;
import com.codearena.api.contest.dto.LeaderboardEntry;
import com.codearena.api.contest.dto.LeaderboardResponse;
import com.codearena.api.contest.scoring.ScoredSubmission;
import com.codearena.api.submission.Submission;
import com.codearena.api.submission.SubmissionRepository;
import com.codearena.api.support.DockerAvailability;
import com.codearena.api.user.Role;
import com.codearena.api.user.User;
import com.codearena.api.user.UserRepository;
import com.codearena.common.Language;
import com.codearena.common.Verdict;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Redis (Testcontainers) behind LeaderboardService; the database side is mocked. Pulled forward from Phase 6.
 */
class LeaderboardRedisTest {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);
    private static final Instant START = Instant.parse("2026-10-13T10:00:00Z");
    private static final long CONTEST_ID = 9L;
    private static final Map<Long, String> USERNAMES = Map.of(1L, "alice", 2L, "bob", 3L, "carol", 4L, "dave",
            5L, "erin");

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private final ContestRepository contestRepository = mock(ContestRepository.class);
    private final ContestParticipantRepository participantRepository = mock(ContestParticipantRepository.class);
    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final List<ScoredSubmission> submissions = new ArrayList<>();
    private LeaderboardService service;
    private long nextSubmissionId = 1;

    @BeforeAll
    static void startRedis() {
        DockerAvailability.requireDocker("leaderboard redis test");
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (REDIS.isRunning()) {
            REDIS.stop();
        }
    }

    @BeforeEach
    void contestWithFourParticipants() {
        flushAll();
        Contest contest = new Contest("Weekly #1", START, START.plus(Duration.ofHours(2)), 20, 1L);
        ReflectionTestUtils.setField(contest, "id", CONTEST_ID);
        when(contestRepository.findById(CONTEST_ID)).thenReturn(Optional.of(contest));
        when(participantRepository.findUserIds(CONTEST_ID)).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(submissionRepository.findScoredSubmissions(CONTEST_ID)).thenAnswer(inv -> List.copyOf(submissions));
        for (long userId = 1; userId <= 5; userId++) {
            long id = userId;
            when(submissionRepository.findScoredSubmissions(CONTEST_ID, id)).thenAnswer(inv ->
                    submissions.stream().filter(s -> s.userId() == id).toList());
        }
        when(userRepository.findAllById(anyIterable())).thenAnswer(inv -> {
            Iterable<Long> ids = inv.getArgument(0);
            return StreamSupport.stream(ids.spliterator(), false).map(LeaderboardRedisTest::user).toList();
        });
        service = new LeaderboardService(redis, contestRepository, participantRepository, submissionRepository,
                userRepository, new LeaderboardProperties(50, 200));

        submit(1L, 101L, Verdict.ACCEPTED, 10);
        submit(1L, 102L, Verdict.WRONG_ANSWER, 20);
        submit(1L, 102L, Verdict.ACCEPTED, 35);
        submit(2L, 101L, Verdict.ACCEPTED, 30);
        submit(2L, 102L, Verdict.ACCEPTED, 35);
        submit(3L, 101L, Verdict.WRONG_ANSWER, 5);
        submit(3L, 101L, Verdict.ACCEPTED, 12);
        submit(3L, 102L, Verdict.COMPILATION_ERROR, 15);
    }

    @Test
    void missingKeyIsRebuiltFromDatabaseWithSharedRanksForTies() {
        assertThat(redis.hasKey(LeaderboardService.key(CONTEST_ID))).isFalse();

        LeaderboardResponse board = service.top(CONTEST_ID, null);

        assertThat(board.entries()).containsExactly(
                new LeaderboardEntry(1, 1L, "alice", 2, 65),
                new LeaderboardEntry(1, 2L, "bob", 2, 65),
                new LeaderboardEntry(3, 3L, "carol", 1, 32),
                new LeaderboardEntry(4, 4L, "dave", 0, 0));
        assertThat(redis.opsForZSet().score(LeaderboardService.key(CONTEST_ID), "1")).isEqualTo(2 * 10_000_000d - 65);
        assertThat(redis.keys(LeaderboardService.key(CONTEST_ID) + ":rebuild:*")).isEmpty();
    }

    @Test
    void finalizedSubmissionRecomputesThatUsersScoreFromSource() {
        service.top(CONTEST_ID, null);
        Submission carolsAccepted = submissionEntity(3L);
        when(submissionRepository.findById(500L)).thenReturn(Optional.of(carolsAccepted));
        submit(3L, 102L, Verdict.ACCEPTED, 50);

        service.refreshForSubmission(500L);

        assertThat(service.top(CONTEST_ID, null).entries()).extracting(LeaderboardEntry::username,
                        LeaderboardEntry::rank, LeaderboardEntry::solved, LeaderboardEntry::penaltyMinutes)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("alice", 1, 2, 65L),
                        org.assertj.core.groups.Tuple.tuple("bob", 1, 2, 65L),
                        org.assertj.core.groups.Tuple.tuple("carol", 3, 2, 82L),
                        org.assertj.core.groups.Tuple.tuple("dave", 4, 0, 0L));
    }

    @Test
    void joinAddsZeroScoreWithoutOverwritingExistingScores() {
        service.top(CONTEST_ID, null);

        service.recordJoin(CONTEST_ID, 5L);
        service.recordJoin(CONTEST_ID, 1L);

        assertThat(redis.opsForZSet().score(LeaderboardService.key(CONTEST_ID), "5")).isZero();
        assertThat(redis.opsForZSet().score(LeaderboardService.key(CONTEST_ID), "1")).isEqualTo(2 * 10_000_000d - 65);
    }

    @Test
    void flushAllThenReadRebuildsTheSameLeaderboard() {
        LeaderboardResponse before = service.top(CONTEST_ID, null);

        flushAll();
        LeaderboardResponse after = service.top(CONTEST_ID, null);

        assertThat(after).isEqualTo(before);
    }

    @Test
    void limitReturnsTopEntries() {
        assertThat(service.top(CONTEST_ID, 2).entries()).extracting(LeaderboardEntry::username)
                .containsExactly("alice", "bob");
    }

    @Test
    void contestWithoutParticipantsHasEmptyLeaderboard() {
        when(participantRepository.findUserIds(CONTEST_ID)).thenReturn(List.of());

        assertThat(service.top(CONTEST_ID, null).entries()).isEmpty();
        assertThat(redis.hasKey(LeaderboardService.key(CONTEST_ID))).isFalse();
    }

    private void submit(long userId, long problemId, Verdict verdict, int minutesAfterStart) {
        submissions.add(new ScoredSubmission(nextSubmissionId++, userId, problemId, verdict,
                START.plus(Duration.ofMinutes(minutesAfterStart))));
    }

    private static Submission submissionEntity(long userId) {
        return new Submission(userId, 102L, CONTEST_ID, Language.PYTHON, "print(1)", START);
    }

    private static User user(long id) {
        User user = new User(USERNAMES.get(id), USERNAMES.get(id) + "@example.com", "hash", Role.USER, START);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static void flushAll() {
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }
}
