package com.codearena.api.websocket;

import com.codearena.api.contest.ContestRepository;
import com.codearena.api.security.AuthUser;
import com.codearena.api.submission.SubmissionRepository;
import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Server-to-client only: every client SEND is rejected, and SUBSCRIBE is default-deny with two allowed destinations.
 */
@Component
public class StompDestinationInterceptor implements ChannelInterceptor {

    static final String ACCESS_DENIED = "Access denied";
    static final String SEND_NOT_ALLOWED = "Clients may not send messages";

    private static final Pattern SUBMISSION_TOPIC = Pattern.compile("^/topic/submissions/(\\d{1,18})$");
    private static final Pattern LEADERBOARD_TOPIC = Pattern.compile("^/topic/contests/(\\d{1,18})/leaderboard$");

    private final SubmissionRepository submissionRepository;
    private final ContestRepository contestRepository;

    public StompDestinationInterceptor(SubmissionRepository submissionRepository, ContestRepository contestRepository) {
        this.submissionRepository = submissionRepository;
        this.contestRepository = contestRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        SimpMessageType type = accessor.getMessageType();
        if (type == SimpMessageType.MESSAGE) {
            throw new StompRejectedException(SEND_NOT_ALLOWED);
        }
        if (type == SimpMessageType.SUBSCRIBE && !mayReceive(authUser(accessor.getUser()), accessor.getDestination())) {
            throw new StompRejectedException(ACCESS_DENIED);
        }
        return message;
    }

    private boolean mayReceive(AuthUser user, String destination) {
        if (user == null || destination == null) {
            return false;
        }
        Matcher submission = SUBMISSION_TOPIC.matcher(destination);
        if (submission.matches()) {
            return submissionRepository.findOwnerId(Long.parseLong(submission.group(1)))
                    .map(owner -> user.isAdmin() || owner == user.id())
                    .orElse(false);
        }
        Matcher leaderboard = LEADERBOARD_TOPIC.matcher(destination);
        if (leaderboard.matches()) {
            return contestRepository.existsById(Long.parseLong(leaderboard.group(1)));
        }
        return false;
    }

    private static AuthUser authUser(Principal principal) {
        return principal instanceof Authentication auth && auth.getPrincipal() instanceof AuthUser user ? user : null;
    }
}
