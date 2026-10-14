package com.codearena.api.judging;

import com.codearena.common.Language;

public record SubmissionCreatedEvent(long submissionId, Language language) {
}
