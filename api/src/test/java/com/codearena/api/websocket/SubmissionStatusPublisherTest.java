package com.codearena.api.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codearena.api.judging.SubmissionStatusChangedEvent;
import com.codearena.api.submission.Submission;
import com.codearena.api.submission.SubmissionRepository;
import com.codearena.api.submission.dto.SubmissionStatusUpdate;
import com.codearena.common.Language;
import com.codearena.common.Verdict;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class SubmissionStatusPublisherTest {

    private static final String SOURCE = "print('SECRET_SOURCE_1d4e')";
    private static final String COMPILER_OUTPUT = "File \"/input/Main.py\", line 1\n    print('SECRET_SOURCE_1d4e'\n";

    private final SubmissionRepository repository = mock(SubmissionRepository.class);
    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final SubmissionStatusPublisher publisher = new SubmissionStatusPublisher(repository, template);

    @Test
    void pushesStatusToTheSubmissionTopicWithoutSourceOrDetail() throws Exception {
        Submission submission = new Submission(1L, 5L, 9L, Language.PYTHON, SOURCE, Instant.parse("2026-10-13T10:00:00Z"));
        ReflectionTestUtils.setField(submission, "id", 42L);
        ReflectionTestUtils.setField(submission, "status", Verdict.COMPILATION_ERROR);
        ReflectionTestUtils.setField(submission, "verdictDetail", COMPILER_OUTPUT);
        when(repository.findById(42L)).thenReturn(Optional.of(submission));

        publisher.onStatusChanged(new SubmissionStatusChangedEvent(42L, Verdict.COMPILATION_ERROR));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/submissions/42"), payload.capture());
        SubmissionStatusUpdate update = (SubmissionStatusUpdate) payload.getValue();
        assertThat(update.status()).isEqualTo(Verdict.COMPILATION_ERROR);
        assertThat(update.contestId()).isEqualTo(9L);
        assertThat(update.detailAvailable()).isTrue();
        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(update);
        assertThat(json).doesNotContain("SECRET_SOURCE", "sourceCode", "verdictDetail");
    }

    @Test
    void missingSubmissionPushesNothing() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        publisher.onStatusChanged(new SubmissionStatusChangedEvent(42L, Verdict.RUNNING));

        verify(template, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void brokerFailureIsContained() {
        Submission submission = new Submission(1L, 5L, null, Language.PYTHON, SOURCE, Instant.now());
        ReflectionTestUtils.setField(submission, "id", 42L);
        when(repository.findById(42L)).thenReturn(Optional.of(submission));
        doThrow(new MessagingException("broker down")).when(template).convertAndSend(anyString(), any(Object.class));

        publisher.onStatusChanged(new SubmissionStatusChangedEvent(42L, Verdict.RUNNING));
    }
}
