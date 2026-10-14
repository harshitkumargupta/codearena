package com.codearena.api.submission;

import com.codearena.common.Language;
import java.time.Instant;

public record SubmissionTiming(Language language, Instant createdAt) {
}
