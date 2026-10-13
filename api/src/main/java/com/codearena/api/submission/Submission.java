package com.codearena.api.submission;

import com.codearena.common.Language;
import com.codearena.common.Verdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "submissions")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(name = "contest_id")
    private Long contestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Language language;

    @Column(name = "source_code", nullable = false)
    private String sourceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Verdict status;

    @Column(name = "verdict_detail")
    private String verdictDetail;

    @Column(name = "failed_test_ordinal")
    private Integer failedTestOrdinal;

    @Column(name = "time_ms")
    private Integer timeMs;

    @Column(name = "memory_kb")
    private Integer memoryKb;

    @Column(name = "attempts_count", nullable = false)
    private int attemptsCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "judged_at")
    private Instant judgedAt;

    @Column(name = "last_queued_at")
    private Instant lastQueuedAt;

    protected Submission() {
    }

    public Submission(Long userId, Long problemId, Long contestId, Language language, String sourceCode,
                      Instant createdAt) {
        this.userId = userId;
        this.problemId = problemId;
        this.contestId = contestId;
        this.language = language;
        this.sourceCode = sourceCode;
        this.status = Verdict.PENDING;
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

    public Long getContestId() {
        return contestId;
    }

    public Language getLanguage() {
        return language;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public Verdict getStatus() {
        return status;
    }

    public String getVerdictDetail() {
        return verdictDetail;
    }

    public Integer getFailedTestOrdinal() {
        return failedTestOrdinal;
    }

    public Integer getTimeMs() {
        return timeMs;
    }

    public Integer getMemoryKb() {
        return memoryKb;
    }

    public int getAttemptsCount() {
        return attemptsCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getJudgedAt() {
        return judgedAt;
    }

    public Instant getLastQueuedAt() {
        return lastQueuedAt;
    }
}
