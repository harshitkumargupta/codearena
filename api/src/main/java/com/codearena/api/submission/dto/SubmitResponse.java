package com.codearena.api.submission.dto;

import com.codearena.common.Verdict;

public record SubmitResponse(long id, Verdict status) {
}
