package com.codearena.api.contest;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "contest_participants")
public class ContestParticipant {

    @EmbeddedId
    private ContestParticipantId id;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected ContestParticipant() {
    }

    public Long getUserId() {
        return id.getUserId();
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
