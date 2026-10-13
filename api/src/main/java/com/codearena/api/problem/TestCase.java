package com.codearena.api.problem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_cases")
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Column(nullable = false)
    private String input;

    @Column(name = "expected_output", nullable = false)
    private String expectedOutput;

    @Column(name = "is_sample", nullable = false)
    private boolean sample;

    @Column(nullable = false)
    private int ordinal;

    protected TestCase() {
    }

    public TestCase(Long problemId, String input, String expectedOutput, boolean sample, int ordinal) {
        this.problemId = problemId;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.sample = sample;
        this.ordinal = ordinal;
    }

    public Long getId() {
        return id;
    }

    public Long getProblemId() {
        return problemId;
    }

    public String getInput() {
        return input;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public boolean isSample() {
        return sample;
    }

    public int getOrdinal() {
        return ordinal;
    }
}
