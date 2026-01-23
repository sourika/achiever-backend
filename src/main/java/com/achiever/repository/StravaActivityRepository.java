package com.achiever.repository;

import com.achiever.entity.StravaActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface StravaActivityRepository extends JpaRepository<StravaActivity, Long> {

    List<StravaActivity> findByUserIdOrderByStartDateDesc(UUID userId);

    @Query("""
        SELECT a FROM StravaActivity a 
        WHERE a.user.id = :userId 
        AND a.sportType = :sportType 
        AND a.startDate >= :startDate 
        AND a.startDate < :endDate
        """)
    List<StravaActivity> findByUserIdAndSportTypeAndDateRange(
            UUID userId, 
            String sportType, 
            OffsetDateTime startDate, 
            OffsetDateTime endDate);

    @Query("""
        SELECT COALESCE(SUM(a.distanceMeters), 0) FROM StravaActivity a 
        WHERE a.user.id = :userId 
        AND a.sportType = :sportType 
        AND a.startDate >= :startDate 
        AND a.startDate < :endDate
        """)
    int sumDistanceByUserAndSportTypeAndDateRange(
            UUID userId, 
            String sportType, 
            OffsetDateTime startDate, 
            OffsetDateTime endDate);

    boolean existsById(Long id);
}
