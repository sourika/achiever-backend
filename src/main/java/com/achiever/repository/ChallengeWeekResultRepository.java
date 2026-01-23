package com.achiever.repository;

import com.achiever.entity.ChallengeWeekResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChallengeWeekResultRepository extends JpaRepository<ChallengeWeekResult, UUID> {

    List<ChallengeWeekResult> findByChallengeIdOrderByWeekStartDesc(UUID challengeId);

    Optional<ChallengeWeekResult> findByChallengeIdAndWeekStart(UUID challengeId, LocalDate weekStart);

    boolean existsByChallengeIdAndWeekStart(UUID challengeId, LocalDate weekStart);
}
