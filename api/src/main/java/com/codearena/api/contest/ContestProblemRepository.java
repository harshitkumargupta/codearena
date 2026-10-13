package com.codearena.api.contest;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContestProblemRepository extends JpaRepository<ContestProblem, ContestProblemId> {

    List<ContestProblem> findByIdContestIdOrderByLabelAsc(Long contestId);

    @Query("""
            select count(cp) > 0
              from ContestProblem cp, Contest c
             where c.id = cp.id.contestId
               and cp.id.problemId = :problemId
               and c.startTime <= :now and c.endTime > :now
            """)
    boolean isInRunningContest(@Param("problemId") long problemId, @Param("now") Instant now);
}
