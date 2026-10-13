package com.codearena.api.submission.dto;

import com.codearena.api.web.MaxUtf8Bytes;
import com.codearena.common.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SubmitRequest(
        @NotNull @Positive Long problemId,
        @NotNull Language language,
        @NotBlank @MaxUtf8Bytes(SubmitRequest.MAX_SOURCE_BYTES) String sourceCode,
        @Positive Long contestId
) {

    public static final int MAX_SOURCE_BYTES = 64 * 1024;
}
