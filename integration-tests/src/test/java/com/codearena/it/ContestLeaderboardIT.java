package com.codearena.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.codearena.api.contest.leaderboard.LeaderboardService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * A running contest end to end: join, a wrong then a correct submission, and the Redis leaderboard holds the ICPC
 * score (1 solved, penalty = minutes to the accepted submission + 20 per earlier wrong attempt).
 */
@ExtendWith(CodeArenaStackExtension.class)
class ContestLeaderboardIT {

    private static final int PENALTY_PER_WRONG = 20;

    private static CodeArenaStack stack;
    private static ApiClient client;

    @BeforeAll
    static void startStack(CodeArenaStack started) {
        stack = started;
        client = new ApiClient(stack.baseUrl());
    }

    @Test
    void joinSubmitAndTheLeaderboardMatchesTheIcpcScore() {
        ApiClient.User admin = client.login(CodeArenaStack.ADMIN_USERNAME, CodeArenaStack.ADMIN_PASSWORD);
        long twoSum = client.problemId("two-sum");
        Instant start = Instant.now().minus(Duration.ofMinutes(3)).truncatedTo(ChronoUnit.SECONDS);
        ApiClient.Response created = client.send("POST", "/api/admin/contests", admin.token(), Map.of(
                "title", "IT contest " + start.getEpochSecond(),
                "startTime", start.toString(),
                "endTime", start.plus(Duration.ofHours(2)).toString(),
                "penaltyMinutes", PENALTY_PER_WRONG,
                "problems", List.of(Map.of("problemId", twoSum, "label", "A"))));
        assertThat(created.status()).as("create contest: %s", created.body()).isEqualTo(201);
        long contestId = created.body().get("id").asLong();

        ApiClient.User user = client.register("ctst");
        assertThat(client.send("POST", "/api/contests/" + contestId + "/join", user.token(), null).status()).isEqualTo(200);

        ApiClient.Response tutor = client.send("POST", "/api/tutor/hint", user.token(), Map.of("problemId", twoSum, "level", 1));
        assertThat(tutor.status()).isEqualTo(403);
        assertThat(tutor.body().get("message").asText()).contains("contest");

        long wrongId = client.submit(user, twoSum, "PYTHON", Solutions.TWO_SUM_PYTHON_WRONG, contestId);
        assertThat(client.awaitVerdict(user, wrongId).get("status").asText()).isEqualTo("WRONG_ANSWER");
        long acceptedId = client.submit(user, twoSum, "JAVA", Solutions.TWO_SUM_JAVA, contestId);
        JsonNode accepted = client.awaitVerdict(user, acceptedId);
        assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTED");

        long expectedPenalty = Duration.between(start, Instant.parse(accepted.get("createdAt").asText())).toMinutes()
                + PENALTY_PER_WRONG;
        JsonNode entry = awaitLeaderboardEntry(user, contestId);
        assertThat(entry.get("rank").asInt()).isEqualTo(1);
        assertThat(entry.get("solved").asInt()).isEqualTo(1);
        assertThat(entry.get("penaltyMinutes").asLong()).isEqualTo(expectedPenalty);

        Double score = stack.apiBean(StringRedisTemplate.class).opsForZSet()
                .score(LeaderboardService.key(contestId), Long.toString(user.id()));
        assertThat(score).isEqualTo(10_000_000d - expectedPenalty);
    }

    private static JsonNode awaitLeaderboardEntry(ApiClient.User user, long contestId) {
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            JsonNode entries = client.send("GET", "/api/contests/" + contestId + "/leaderboard", null, null).body().get("entries");
            for (JsonNode entry : entries) {
                if (entry.get("userId").asLong() == user.id() && entry.get("solved").asInt() == 1) {
                    return entry;
                }
            }
            ApiClient.pause();
        }
        throw new AssertionError("Leaderboard never showed the accepted submission");
    }
}
