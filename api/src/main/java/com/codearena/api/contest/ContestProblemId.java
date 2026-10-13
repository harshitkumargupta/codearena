package com.codearena.api.contest;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ContestProblemId implements Serializable {

    @Column(name = "contest_id")
    private Long contestId;

    @Column(name = "problem_id")
    private Long problemId;

    protected ContestProblemId() {
    }

    public ContestProblemId(Long contestId, Long problemId) {
        this.contestId = contestId;
        this.problemId = problemId;
    }

    public Long getContestId() {
        return contestId;
    }

    public Long getProblemId() {
        return problemId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ContestProblemId other
                && Objects.equals(contestId, other.contestId) && Objects.equals(problemId, other.problemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contestId, problemId);
    }
}
