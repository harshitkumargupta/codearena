package com.codearena.api.tutor.dto;

import com.codearena.api.tutor.HintKind;

/**
 * {@code fallback} is true when the model's answers broke the code rules and a fixed message was returned instead.
 */
public record TutorResponse(HintKind kind, Integer level, String text, boolean fallback) {
}
