package com.achiever.repository;

import com.achiever.entity.DailyProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyProgressRepository extends JpaRepository<DailyProgress, UUID> {

    Optional<DailyProgress> findByChallengeIdAndUserIdAndDate(UUID challengeId, UUID userId, LocalDate date);

    List<DailyProgress> findByChallengeIdAndUserId(UUID challengeId, UUID userId);

    List<DailyProgress> findByChallengeIdAndDate(UUID challengeId, LocalDate date);

    @Query("""
        SELECT dp FROM DailyProgress dp 
        WHERE dp.challenge.id = :challengeId 
        ORDER BY dp.date DESC, dp.user.id
        """)
    List<DailyProgress> findLatestByChallengeId(UUID challengeId);

    @Query("""
        SELECT dp FROM DailyProgress dp 
        WHERE dp.challenge.id = :challengeId 
        AND dp.date = (SELECT MAX(dp2.date) FROM DailyProgress dp2 WHERE dp2.challenge.id = :challengeId)
        """)
    List<DailyProgress> findCurrentProgressByChallengeId(UUID challengeId);

    @Query("SELECT dp FROM DailyProgress dp WHERE dp.challenge.id = :challengeId AND dp.user.id = :userId ORDER BY dp.date DESC LIMIT 1")
    Optional<DailyProgress> findLatestByChallengeIdAndUserId(@Param("challengeId") UUID challengeId, @Param("userId") UUID userId);
}
