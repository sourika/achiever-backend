package com.achiever.repository;

import com.achiever.entity.ChallengeParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChallengeParticipantRepository extends JpaRepository<ChallengeParticipant, UUID> {

    List<ChallengeParticipant> findByChallengeId(UUID challengeId);

    Optional<ChallengeParticipant> findByChallengeIdAndUserId(UUID challengeId, UUID userId);

    boolean existsByChallengeIdAndUserId(UUID challengeId, UUID userId);

    @Query("SELECT COUNT(p) FROM ChallengeParticipant p WHERE p.challenge.id = :challengeId")
    int countByChallengeId(UUID challengeId);

    @Query("""
        SELECT DISTINCT p.user.id FROM ChallengeParticipant p 
        WHERE p.challenge.status = 'ACTIVE'
        """)
    List<UUID> findActiveParticipantUserIds();
}
