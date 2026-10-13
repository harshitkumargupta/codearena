package com.codearena.api.submission;

import com.codearena.common.Verdict;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Verdict> OPEN_STATUSES = List.of(Verdict.PENDING, Verdict.RUNNING);

    Page<Submission> findByUserId(Long userId, Pageable pageable);

    Page<Submission> findByUserIdAndProblemId(Long userId, Long problemId, Pageable pageable);

    default int markRunning(long id) {
        return updateStatusIfCurrent(id, Verdict.PENDING, Verdict.RUNNING);
    }

    default int finishIfOpen(long id, Verdict verdict, String detail, Integer failedTestOrdinal, Integer timeMs,
                             Integer memoryKb, Instant judgedAt) {
        return finishIfStatusIn(id, verdict, detail, failedTestOrdinal, timeMs, memoryKb, judgedAt, OPEN_STATUSES);
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Submission s set s.status = :next where s.id = :id and s.status = :current")
    int updateStatusIfCurrent(@Param("id") long id, @Param("current") Verdict current, @Param("next") Verdict next);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Submission s
               set s.status = :verdict, s.verdictDetail = :detail, s.failedTestOrdinal = :failedTestOrdinal,
                   s.timeMs = :timeMs, s.memoryKb = :memoryKb, s.judgedAt = :judgedAt
             where s.id = :id and s.status in :open
            """)
    int finishIfStatusIn(@Param("id") long id, @Param("verdict") Verdict verdict, @Param("detail") String detail,
                         @Param("failedTestOrdinal") Integer failedTestOrdinal, @Param("timeMs") Integer timeMs,
                         @Param("memoryKb") Integer memoryKb, @Param("judgedAt") Instant judgedAt,
                         @Param("open") List<Verdict> open);
}
