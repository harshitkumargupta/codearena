package com.codearena.api.problem;

import com.codearena.api.problem.dto.AddTestCasesRequest;
import com.codearena.api.problem.dto.AdminProblemResponse;
import com.codearena.api.problem.dto.ProblemRequest;
import com.codearena.api.problem.dto.TestCaseSummary;
import com.codearena.api.security.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/problems")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProblemController {

    private final AdminProblemService adminProblemService;

    public AdminProblemController(AdminProblemService adminProblemService) {
        this.adminProblemService = adminProblemService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProblemResponse create(@Valid @RequestBody ProblemRequest request,
                                       @AuthenticationPrincipal AuthUser admin) {
        return adminProblemService.create(request, admin.id());
    }

    @PutMapping("/{id}")
    public AdminProblemResponse update(@PathVariable long id, @Valid @RequestBody ProblemRequest request) {
        return adminProblemService.update(id, request);
    }

    @PostMapping("/{id}/testcases")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TestCaseSummary> addTestCases(@PathVariable long id, @Valid @RequestBody AddTestCasesRequest request) {
        return adminProblemService.addTestCases(id, request);
    }
}
