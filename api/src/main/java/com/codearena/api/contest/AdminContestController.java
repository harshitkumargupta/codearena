package com.codearena.api.contest;

import com.codearena.api.contest.dto.ContestDetail;
import com.codearena.api.contest.dto.ContestRequest;
import com.codearena.api.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/contests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminContestController {

    private final AdminContestService adminContestService;

    public AdminContestController(AdminContestService adminContestService) {
        this.adminContestService = adminContestService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContestDetail create(@Valid @RequestBody ContestRequest request, @AuthenticationPrincipal AuthUser admin) {
        return adminContestService.create(request, admin.id());
    }
}
