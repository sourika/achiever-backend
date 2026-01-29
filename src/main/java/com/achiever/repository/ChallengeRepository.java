package com.achiever.repository;

import com.achiever.entity.Challenge;
import com.achiever.entity.ChallengeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {

    Optional<Challenge> findByInviteCode(String inviteCode);

    @Query("SELECT c FROM Challenge c LEFT JOIN FETCH c.participants WHERE c.id = :id")
    Optional<Challenge> findByIdWithParticipants(UUID id);

    @Query("SELECT c FROM Challenge c LEFT JOIN FETCH c.participants WHERE c.inviteCode = :code")
    Optional<Challenge> findByInviteCodeWithParticipants(String code);

    List<Challenge> findByStatus(ChallengeStatus status);

    @Query("SELECT c FROM Challenge c WHERE c.status = :status AND c.startAt <= :date")
    List<Challenge> findByStatusAndStartAtBefore(ChallengeStatus status, LocalDate date);

    @Query("SELECT c FROM Challenge c WHERE c.status = :status AND c.endAt < :date")
    List<Challenge> findByStatusAndEndAtBefore(ChallengeStatus status, LocalDate date);

    @Query("""
        SELECT DISTINCT c FROM Challenge c
        JOIN c.participants p
        WHERE p.user.id = :userId
        ORDER BY c.createdAt DESC
        """)
    List<Challenge> findByParticipantUserId(UUID userId);

    @Query("""
        SELECT DISTINCT c FROM Challenge c
        JOIN c.participants p
        WHERE p.user.id = :userId AND c.status = :status
        ORDER BY c.createdAt DESC
        """)
    List<Challenge> findByParticipantUserIdAndStatus(UUID userId, ChallengeStatus status);
}
