package com.codearena.api.problem;

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
@Table(name = "problems")
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "statement_md", nullable = false)
    private String statementMd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Difficulty difficulty;

    @Column(name = "time_limit_ms", nullable = false)
    private int timeLimitMs;

    @Column(name = "memory_limit_mb", nullable = false)
    private int memoryLimitMb;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Problem() {
    }

    public Problem(Long createdBy, Instant createdAt) {
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public void update(String slug, String title, String statementMd, Difficulty difficulty,
                       int timeLimitMs, int memoryLimitMb, boolean published) {
        this.slug = slug;
        this.title = title;
        this.statementMd = statementMd;
        this.difficulty = difficulty;
        this.timeLimitMs = timeLimitMs;
        this.memoryLimitMb = memoryLimitMb;
        this.published = published;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getStatementMd() {
        return statementMd;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public int getTimeLimitMs() {
        return timeLimitMs;
    }

    public int getMemoryLimitMb() {
        return memoryLimitMb;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public boolean isPublished() {
        return published;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
