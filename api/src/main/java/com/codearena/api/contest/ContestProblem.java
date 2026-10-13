package com.codearena.api.contest;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "contest_problems")
public class ContestProblem {

    @EmbeddedId
    private ContestProblemId id;

    @Column(nullable = false, length = 4)
    private String label;

    protected ContestProblem() {
    }

    public ContestProblem(Long contestId, Long problemId, String label) {
        this.id = new ContestProblemId(contestId, problemId);
        this.label = label;
    }

    public Long getContestId() {
        return id.getContestId();
    }

    public Long getProblemId() {
        return id.getProblemId();
    }

    public String getLabel() {
        return label;
    }
}
