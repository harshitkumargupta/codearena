package com.codearena.api.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codearena.api.contest.Contest;
import com.codearena.api.contest.ContestRepository;
import com.codearena.api.judging.SubmissionResultService;
import com.codearena.api.problem.ProblemRepository;
import com.codearena.api.security.AuthUser;
import com.codearena.api.security.JwtProperties;
import com.codearena.api.security.JwtService;
import com.codearena.api.submission.Submission;
import com.codearena.api.submission.SubmissionRepository;
import com.codearena.api.support.DockerAvailability;
import com.codearena.api.user.Role;
import com.codearena.api.user.User;
import com.codearena.api.user.UserRepository;
import com.codearena.common.JudgeResult;
import com.codearena.common.Language;
import com.codearena.common.Verdict;
import java.lang.reflect.Type;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Real STOMP client against the running api with a real PostgreSQL (Testcontainers, pulled forward from Phase 6).
 * RabbitMQ listeners and the sweeper are switched off; Redis is not touched by these paths.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "codearena.sweeper.enabled=false"
})
@ActiveProfiles("test")
class WebSocketIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    private static final Duration WAIT = Duration.ofSeconds(5);

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ContestRepository contestRepository;

    @Autowired
    private SubmissionResultService resultService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private WebSocketStompClient stompClient;
    private AuthUser alice;
    private AuthUser bob;
    private AuthUser root;
    private long submissionId;
    private long contestId;

    @BeforeAll
    static void startPostgres() {
        DockerAvailability.requireDocker("websocket integration test");
        POSTGRES.start();
    }

    @AfterAll
    static void stopPostgres() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void seed() {
        alice = user("ws_alice", Role.USER);
        bob = user("ws_bob", Role.USER);
        root = user("ws_root", Role.ADMIN);
        long problemId = problemRepository.findBySlugAndPublishedTrue("two-sum").orElseThrow().getId();
        submissionId = submissionRepository.save(new Submission(alice.id(), problemId, null, Language.PYTHON,
                "print('SECRET_SOURCE_1d4e')", Instant.now())).getId();
        contestId = contestRepository.save(new Contest("WS Cup", Instant.now(), Instant.now().plusSeconds(3600), 20,
                root.id())).getId();
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new CompositeMessageConverter(
                List.of(new StringMessageConverter(), new MappingJackson2MessageConverter())));
    }

    @AfterEach
    void stopClient() {
        stompClient.stop();
    }

    @Test
    void connectWithoutTokenIsRejected() {
        assertConnectRejected(null);
    }

    @Test
    void connectWithMalformedTokenIsRejected() {
        assertConnectRejected("Bearer not-a-jwt");
    }

    @Test
    void connectWithExpiredTokenIsRejected() {
        JwtService pastJwt = new JwtService(jwtProperties,
                Clock.fixed(Instant.now().minus(jwtProperties.accessTokenTtl()).minusSeconds(60), ZoneOffset.UTC));

        assertConnectRejected("Bearer " + pastJwt.issue(alice));
    }

    @Test
    void ownerReceivesRunningThenFinalVerdictPushedAfterCommit() throws Exception {
        Client client = connect(alice);
        BlockingQueue<Map<String, Object>> frames = client.subscribeAndAwait(StompDestinations.submission(submissionId));

        resultService.apply(JudgeResult.running(submissionId));
        resultService.apply(new JudgeResult(submissionId, Verdict.ACCEPTED, null, 41, 4800, null));

        Map<String, Object> running = frames.poll(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        Map<String, Object> accepted = frames.poll(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        assertThat(running).containsEntry("status", "RUNNING").containsEntry("id", (int) submissionId);
        assertThat(accepted).containsEntry("status", "ACCEPTED").containsEntry("timeMs", 41)
                .containsEntry("detailAvailable", false);
        assertThat(accepted).doesNotContainKeys("sourceCode", "verdictDetail");
        assertThat(String.valueOf(accepted)).doesNotContain("SECRET_SOURCE");
    }

    @Test
    void anotherUserIsDeniedOnSomeoneElsesSubmission() throws Exception {
        connect(bob).assertSubscriptionDenied(StompDestinations.submission(submissionId), "Access denied");
    }

    @Test
    void adminMaySubscribeToAnySubmission() throws Exception {
        connect(root).subscribeAndAwait(StompDestinations.submission(submissionId));
    }

    @Test
    void leaderboardOfExistingContestIsOpenToAnyAuthenticatedUser() throws Exception {
        connect(bob).subscribeAndAwait(StompDestinations.leaderboard(contestId));
    }

    @Test
    void leaderboardOfUnknownContestIsDenied() throws Exception {
        connect(alice).assertSubscriptionDenied(StompDestinations.leaderboard(contestId + 1000), "Access denied");
    }

    @Test
    void unknownDestinationIsDenied() throws Exception {
        connect(root).assertSubscriptionDenied("/topic/everything", "Access denied");
    }

    @Test
    void clientSendIsDenied() throws Exception {
        Client client = connect(root);

        client.session.send(StompDestinations.submission(submissionId), Map.of("status", "ACCEPTED"));

        assertThat(client.error.get(WAIT.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo("Clients may not send messages");
        client.closed.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void assertConnectRejected(String authorization) {
        Handler handler = new Handler();
        CompletableFuture<StompSession> session = stompClient.connectAsync(url(), new WebSocketHttpHeaders(),
                headers(authorization), handler);

        assertThatThrownBy(() -> session.get(WAIT.toMillis(), TimeUnit.MILLISECONDS))
                .isInstanceOf(ExecutionException.class);
        assertThat(handler.error).succeedsWithin(WAIT).isEqualTo("Authentication required");
    }

    private Client connect(AuthUser user) throws Exception {
        Handler handler = new Handler();
        StompSession session = stompClient.connectAsync(url(), new WebSocketHttpHeaders(),
                headers("Bearer " + jwtService.issue(user)), handler).get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        return new Client(session, handler.error, handler.closed);
    }

    private StompHeaders headers(String authorization) {
        StompHeaders headers = new StompHeaders();
        if (authorization != null) {
            headers.add("Authorization", authorization);
        }
        return headers;
    }

    private String url() {
        return "ws://localhost:" + port + StompDestinations.ENDPOINT;
    }

    private AuthUser user(String username, Role role) {
        User user = userRepository.findByUsername(username).orElseGet(() -> userRepository.save(
                new User(username, username + "@example.com", "hash", role, Instant.now())));
        return new AuthUser(user.getId(), user.getUsername(), user.getRole());
    }

    private final class Client {

        private final StompSession session;
        private final CompletableFuture<String> error;
        private final CompletableFuture<Void> closed;

        private Client(StompSession session, CompletableFuture<String> error, CompletableFuture<Void> closed) {
            this.session = session;
            this.error = error;
            this.closed = closed;
        }

        /**
         * Subscribes, then sends probe messages from the server until one arrives, proving the subscription is live.
         */
        BlockingQueue<Map<String, Object>> subscribeAndAwait(String destination) throws InterruptedException {
            BlockingQueue<Map<String, Object>> frames = new LinkedBlockingQueue<>();
            session.subscribe(destination, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Map.class;
                }

                @Override
                @SuppressWarnings("unchecked")
                public void handleFrame(StompHeaders headers, Object payload) {
                    frames.add((Map<String, Object>) payload);
                }
            });
            long deadline = System.nanoTime() + WAIT.toNanos();
            while (System.nanoTime() < deadline) {
                messagingTemplate.convertAndSend(destination, Map.of("probe", true));
                Map<String, Object> frame = frames.poll(100, TimeUnit.MILLISECONDS);
                if (frame != null && frame.containsKey("probe")) {
                    Thread.sleep(100);
                    frames.removeIf(f -> f.containsKey("probe"));
                    assertThat(error).isNotDone();
                    return frames;
                }
            }
            throw new AssertionError("Subscription to " + destination + " never became active; error=" + error.getNow(null));
        }

        void assertSubscriptionDenied(String destination, String expectedError) throws Exception {
            BlockingQueue<Map<String, Object>> frames = new LinkedBlockingQueue<>();
            session.subscribe(destination, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Map.class;
                }

                @Override
                @SuppressWarnings("unchecked")
                public void handleFrame(StompHeaders headers, Object payload) {
                    frames.add((Map<String, Object>) payload);
                }
            });
            assertThat(error.get(WAIT.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo(expectedError);
            closed.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            messagingTemplate.convertAndSend(destination, Map.of("probe", true));
            assertThat(frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
        }
    }

    private static final class Handler extends StompSessionHandlerAdapter {

        private final CompletableFuture<String> error = new CompletableFuture<>();
        private final CompletableFuture<Void> closed = new CompletableFuture<>();

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return String.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            error.complete(headers.getFirst("message"));
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload,
                                    Throwable exception) {
            if (command == StompCommand.ERROR) {
                error.complete(headers.getFirst("message"));
            }
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            closed.complete(null);
        }
    }
}
