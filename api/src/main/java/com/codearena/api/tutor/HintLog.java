package com.codearena.api.tutor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "hint_logs")
public class HintLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(name = "submission_id")
    private Long submissionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HintKind kind;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column
    private Integer level;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HintLog() {
    }

    public HintLog(Long userId, Long problemId, Long submissionId, HintKind kind, Integer level, Instant createdAt) {
        this.userId = userId;
        this.problemId = problemId;
        this.submissionId = submissionId;
        this.kind = kind;
        this.level = level;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProblemId() {
        return problemId;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public HintKind getKind() {
        return kind;
    }

    public Integer getLevel() {
        return level;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
