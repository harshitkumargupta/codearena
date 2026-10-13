package com.codearena.api.submission;

import com.codearena.api.contest.scoring.ScoredSubmission;
import com.codearena.common.Verdict;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Verdict> OPEN_STATUSES = List.of(Verdict.PENDING, Verdict.RUNNING);

    Page<Submission> findByUserId(Long userId, Pageable pageable);

    Page<Submission> findByUserIdAndProblemId(Long userId, Long problemId, Pageable pageable);

    String LOCK_STUCK_SQL = """
            select s.id as "id", s.attempts_count as "attemptsCount"
              from submissions s
             where s.status in ('PENDING', 'RUNNING')
               and coalesce(s.last_queued_at, s.created_at) < :cutoff
             order by s.id
             limit :limit
               for update skip locked
            """;

    @Query("""
            select new com.codearena.api.contest.scoring.ScoredSubmission(
                   s.id, s.userId, s.problemId, s.status, s.createdAt)
              from Submission s
             where s.contestId = :contestId and s.userId = :userId
            """)
    List<ScoredSubmission> findScoredSubmissions(@Param("contestId") long contestId, @Param("userId") long userId);

    @Query("""
            select new com.codearena.api.contest.scoring.ScoredSubmission(
                   s.id, s.userId, s.problemId, s.status, s.createdAt)
              from Submission s
             where s.contestId = :contestId
            """)
    List<ScoredSubmission> findScoredSubmissions(@Param("contestId") long contestId);

    /**
     * Locks open submissions whose last publish is older than {@code cutoff}. Rows locked by another transaction
     * (e.g. a second api instance) are skipped rather than waited for.
     */
    @Query(value = LOCK_STUCK_SQL, nativeQuery = true)
    List<StuckSubmission> lockStuck(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Submission s set s.lastQueuedAt = :queuedAt where s.id = :id")
    int markQueued(@Param("id") long id, @Param("queuedAt") Instant queuedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Submission s set s.attemptsCount = s.attemptsCount + 1, s.lastQueuedAt = :queuedAt where s.id = :id")
    int markRequeued(@Param("id") long id, @Param("queuedAt") Instant queuedAt);

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
