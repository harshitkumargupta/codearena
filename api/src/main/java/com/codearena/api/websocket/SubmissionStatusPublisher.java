package com.codearena.api.websocket;

import com.codearena.api.judging.SubmissionStatusChangedEvent;
import com.codearena.api.submission.SubmissionRepository;
import com.codearena.api.submission.dto.SubmissionStatusUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The single path from a committed status change to {@code /topic/submissions/{id}}.
 */
@Component
public class SubmissionStatusPublisher {

    private static final Logger log = LoggerFactory.getLogger(SubmissionStatusPublisher.class);

    private final SubmissionRepository submissionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public SubmissionStatusPublisher(SubmissionRepository submissionRepository,
                                     SimpMessagingTemplate messagingTemplate) {
        this.submissionRepository = submissionRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onStatusChanged(SubmissionStatusChangedEvent event) {
        try {
            submissionRepository.findById(event.submissionId()).ifPresent(submission -> {
                messagingTemplate.convertAndSend(StompDestinations.submission(submission.getId()),
                        SubmissionStatusUpdate.from(submission));
                log.debug("Pushed {} for submission {}", submission.getStatus(), submission.getId());
            });
        } catch (RuntimeException ex) {
            log.warn("Could not push status of submission {}: {}", event.submissionId(), ex.toString());
        }
    }
}
