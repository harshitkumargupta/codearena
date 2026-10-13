package com.codearena.api.contest;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ContestParticipantRepository extends JpaRepository<ContestParticipant, ContestParticipantId> {

    @Query("select p.id.userId from ContestParticipant p where p.id.contestId = :contestId")
    List<Long> findUserIds(@Param("contestId") Long contestId);

    /**
     * @return 1 if the user was added, 0 if they had already joined
     */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into contest_participants (contest_id, user_id, joined_at)
            values (:contestId, :userId, :joinedAt)
            on conflict (contest_id, user_id) do nothing
            """)
    int joinIfAbsent(@Param("contestId") long contestId, @Param("userId") long userId,
                     @Param("joinedAt") Instant joinedAt);
}
