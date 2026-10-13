package com.codearena.api.contest;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ContestParticipantId implements Serializable {

    @Column(name = "contest_id")
    private Long contestId;

    @Column(name = "user_id")
    private Long userId;

    protected ContestParticipantId() {
    }

    public ContestParticipantId(Long contestId, Long userId) {
        this.contestId = contestId;
        this.userId = userId;
    }

    public Long getContestId() {
        return contestId;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ContestParticipantId other
                && Objects.equals(contestId, other.contestId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contestId, userId);
    }
}
