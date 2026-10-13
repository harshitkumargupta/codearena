package com.codearena.api.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codearena.api.contest.ContestRepository;
import com.codearena.api.security.AuthUser;
import com.codearena.api.submission.SubmissionRepository;
import com.codearena.api.user.Role;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;

class StompDestinationInterceptorTest {

    private static final AuthUser OWNER = new AuthUser(1L, "alice", Role.USER);
    private static final AuthUser OTHER = new AuthUser(2L, "bob", Role.USER);
    private static final AuthUser ADMIN = new AuthUser(3L, "root", Role.ADMIN);

    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final ContestRepository contestRepository = mock(ContestRepository.class);
    private final StompDestinationInterceptor interceptor =
            new StompDestinationInterceptor(submissionRepository, contestRepository);

    @BeforeEach
    void submissionTenOwnedByAliceAndContestNine() {
        when(submissionRepository.findOwnerId(10L)).thenReturn(Optional.of(1L));
        when(submissionRepository.findOwnerId(11L)).thenReturn(Optional.empty());
        when(contestRepository.existsById(9L)).thenReturn(true);
    }

    @Test
    void ownerMaySubscribeToOwnSubmission() {
        assertAllowed(OWNER, "/topic/submissions/10");
    }

    @Test
    void otherUserIsDeniedOnSomeoneElsesSubmission() {
        assertDenied(OTHER, "/topic/submissions/10");
    }

    @Test
    void adminMaySubscribeToAnySubmission() {
        assertAllowed(ADMIN, "/topic/submissions/10");
    }

    @Test
    void unknownSubmissionIsDeniedEvenForAdmin() {
        assertDenied(ADMIN, "/topic/submissions/11");
    }

    @Test
    void anyAuthenticatedUserMaySubscribeToExistingContestLeaderboard() {
        assertAllowed(OTHER, "/topic/contests/9/leaderboard");
    }

    @Test
    void unknownContestLeaderboardIsDenied() {
        assertDenied(ADMIN, "/topic/contests/99/leaderboard");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/anything", "/topic/submissions", "/topic/submissions/10/extra",
            "/topic/submissions/abc", "/topic/submissions/-1", "/topic/submissions/99999999999999999999",
            "/topic/contests/9", "/topic/contests/9/announcements", "/queue/submissions/10", "/app/submit",
            "/topic/submissions/10 ", "/topic/../topic/submissions/10"})
    void unknownOrMalformedDestinationsAreDenied(String destination) {
        assertDenied(ADMIN, destination);
    }

    @Test
    void subscribeWithoutPrincipalIsDenied() {
        assertDenied(null, "/topic/contests/9/leaderboard");
    }

    @Test
    void clientSendIsDeniedEvenToAllowedTopics() {
        assertThatThrownBy(() -> interceptor.preSend(StompFrames.send(ADMIN, "/topic/submissions/10"),
                StompFrames.CHANNEL))
                .isInstanceOf(StompRejectedException.class)
                .hasMessage("Clients may not send messages");
    }

    @ParameterizedTest
    @EnumSource(value = StompCommand.class, names = {"CONNECT", "UNSUBSCRIBE", "DISCONNECT", "ACK", "NACK"})
    void controlFramesPassThrough(StompCommand command) {
        Message<byte[]> frame = StompFrames.of(command);

        assertThat(interceptor.preSend(frame, StompFrames.CHANNEL)).isSameAs(frame);
    }

    private void assertAllowed(AuthUser user, String destination) {
        Message<byte[]> frame = StompFrames.subscribe(user, destination);

        assertThat(interceptor.preSend(frame, StompFrames.CHANNEL)).isSameAs(frame);
    }

    private void assertDenied(AuthUser user, String destination) {
        assertThatThrownBy(() -> interceptor.preSend(StompFrames.subscribe(user, destination), StompFrames.CHANNEL))
                .isInstanceOf(StompRejectedException.class)
                .hasMessage("Access denied");
    }
}
