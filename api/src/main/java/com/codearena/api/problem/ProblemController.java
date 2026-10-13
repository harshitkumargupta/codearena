package com.codearena.api.problem;

import com.codearena.api.problem.dto.ProblemDetail;
import com.codearena.api.problem.dto.ProblemSummary;
import com.codearena.api.web.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping
    public PageResponse<ProblemSummary> list(@RequestParam(required = false) Difficulty difficulty,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return problemService.list(difficulty, page, size);
    }

    @GetMapping("/{slug}")
    public ProblemDetail get(@PathVariable String slug) {
        return problemService.getPublished(slug);
    }
}
