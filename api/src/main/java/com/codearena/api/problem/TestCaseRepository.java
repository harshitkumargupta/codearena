package com.codearena.api.problem;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByProblemIdAndSampleTrueOrderByOrdinalAsc(Long problemId);

    @Query("select coalesce(max(t.ordinal), 0) from TestCase t where t.problemId = :problemId")
    int findMaxOrdinal(@Param("problemId") Long problemId);
}
