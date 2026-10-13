package com.codearena.api.submission;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Page<Submission> findByUserId(Long userId, Pageable pageable);

    Page<Submission> findByUserIdAndProblemId(Long userId, Long problemId, Pageable pageable);
}
