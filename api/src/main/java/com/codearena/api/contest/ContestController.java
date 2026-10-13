package com.codearena.api.contest;

import com.codearena.api.contest.dto.ContestDetail;
import com.codearena.api.contest.dto.ContestSummary;
import com.codearena.api.contest.dto.JoinResponse;
import com.codearena.api.contest.dto.LeaderboardResponse;
import com.codearena.api.contest.leaderboard.LeaderboardService;
import com.codearena.api.security.AuthUser;
import com.codearena.api.web.PageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contests")
public class ContestController {

    private final ContestService contestService;
    private final LeaderboardService leaderboardService;

    public ContestController(ContestService contestService, LeaderboardService leaderboardService) {
        this.contestService = contestService;
        this.leaderboardService = leaderboardService;
    }

    @GetMapping
    public PageResponse<ContestSummary> list(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return contestService.list(page, size);
    }

    @GetMapping("/{id}")
    public ContestDetail get(@PathVariable long id) {
        return contestService.get(id);
    }

    @PostMapping("/{id}/join")
    public JoinResponse join(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return contestService.join(id, user);
    }

    @GetMapping("/{id}/leaderboard")
    public LeaderboardResponse leaderboard(@PathVariable long id, @RequestParam(required = false) Integer limit) {
        return leaderboardService.top(id, limit);
    }
}
