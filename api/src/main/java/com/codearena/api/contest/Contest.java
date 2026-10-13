package com.codearena.api.contest;

import com.codearena.api.contest.scoring.ContestWindow;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "contests")
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "penalty_minutes", nullable = false)
    private int penaltyMinutes;

    @Column(name = "created_by")
    private Long createdBy;

    protected Contest() {
    }

    public Contest(String title, Instant startTime, Instant endTime, int penaltyMinutes, Long createdBy) {
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.penaltyMinutes = penaltyMinutes;
        this.createdBy = createdBy;
    }

    public ContestStatus statusAt(Instant now) {
        if (now.isBefore(startTime)) {
            return ContestStatus.UPCOMING;
        }
        return now.isBefore(endTime) ? ContestStatus.RUNNING : ContestStatus.ENDED;
    }

    public ContestWindow window() {
        return new ContestWindow(startTime, endTime);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public int getPenaltyMinutes() {
        return penaltyMinutes;
    }

    public Long getCreatedBy() {
        return createdBy;
    }
}
