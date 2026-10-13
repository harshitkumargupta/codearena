package com.codearena.api.submission;

import com.codearena.api.security.AuthUser;
import com.codearena.api.submission.dto.SubmissionResponse;
import com.codearena.api.submission.dto.SubmissionSummary;
import com.codearena.api.submission.dto.SubmitRequest;
import com.codearena.api.submission.dto.SubmitResponse;
import com.codearena.api.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubmitResponse submit(@Valid @RequestBody SubmitRequest request, @AuthenticationPrincipal AuthUser user) {
        return submissionService.submit(user, request);
    }

    @GetMapping("/me")
    public PageResponse<SubmissionSummary> mine(@AuthenticationPrincipal AuthUser user,
                                                @RequestParam(required = false) Long problemId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return submissionService.mine(user, problemId, page, size);
    }

    @GetMapping("/{id}")
    public SubmissionResponse get(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return submissionService.get(user, id);
    }
}
