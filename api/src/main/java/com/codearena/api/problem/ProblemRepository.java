package com.codearena.api.problem;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    Page<Problem> findByPublishedTrue(Pageable pageable);

    Page<Problem> findByPublishedTrueAndDifficulty(Difficulty difficulty, Pageable pageable);

    Optional<Problem> findBySlugAndPublishedTrue(String slug);

    boolean existsBySlug(String slug);
}
