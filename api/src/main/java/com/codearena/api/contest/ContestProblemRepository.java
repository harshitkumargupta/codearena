package com.codearena.api.contest;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContestProblemRepository extends JpaRepository<ContestProblem, ContestProblemId> {

    List<ContestProblem> findByIdContestIdOrderByLabelAsc(Long contestId);
}
