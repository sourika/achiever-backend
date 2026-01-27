package com.achiever.strava;

import com.achiever.entity.*;
import com.achiever.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StravaSyncService {

    private final StravaApiClient stravaApiClient;
    private final StravaActivityRepository activityRepository;
    private final StravaConnectionRepository connectionRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final ChallengeRepository challengeRepository;
    private final DailyProgressRepository progressRepository;

    /**
     * Sync activities for all users in active challenges
     * Runs every 10 minutes
     */
    @Scheduled(fixedRate = 600_000) // 10 minutes
    public void syncActiveParticipants() {
        log.info("Starting scheduled Strava sync for active participants");
        
        List<UUID> userIds = participantRepository.findActiveParticipantUserIds();
        log.info("Found {} users in active challenges", userIds.size());

        for (UUID userId : userIds) {
            try {
                syncUserActivities(userId);
                // Small delay to respect rate limits
                Thread.sleep(500);
            } catch (Exception e) {
                log.error("Failed to sync activities for user {}", userId, e);
            }
        }
    }

    /**
     * Sync activities for a specific user
     */
    @Transactional
    public void syncUserActivities(UUID userId) {
        StravaConnection connection = connectionRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("No Strava connection for user " + userId));

        // Fetch last 30 days of activities
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime thirtyDaysAgo = now.minusDays(30);

        List<StravaActivityResponse> activities = stravaApiClient.getActivities(
                connection, thirtyDaysAgo, now, 1, 100);

        log.info("Fetched {} activities for user {}", activities.size(), userId);

        int newCount = 0;
        for (StravaActivityResponse activity : activities) {
            if (!activityRepository.existsById(activity.getId())) {
                StravaActivity entity = StravaActivity.builder()
                        .id(activity.getId())
                        .user(connection.getUser())
                        .sportType(mapSportType(activity.getSportType()))
                        .name(activity.getName())
                        .startDate(activity.getStartDate())
                        .distanceMeters(activity.getDistance() != null ? activity.getDistance().intValue() : 0)
                        .movingTimeSeconds(activity.getMovingTime())
                        .build();

                activityRepository.save(entity);
                newCount++;
            }
        }

        log.info("Saved {} new activities for user {}", newCount, userId);

        // Update progress for active challenges
        updateProgressForUser(userId);
    }

    /**
     * Update daily progress for all active challenges a user is in
     */
    private void updateProgressForUser(UUID userId) {
        List<Challenge> activeChallenges = challengeRepository
                .findByParticipantUserIdAndStatus(userId, ChallengeStatus.ACTIVE);

        for (Challenge challenge : activeChallenges) {
            updateChallengeProgress(challenge, userId);
        }
    }

    /**
     * Calculate and update progress for a user in a challenge (multi-sport)
     */
    @Transactional
    public void updateChallengeProgress(Challenge challenge, UUID userId) {
        ChallengeParticipant participant = participantRepository
                .findByChallengeIdAndUserId(challenge.getId(), userId)
                .orElseThrow();

        // Get challenge date range as OffsetDateTime
        OffsetDateTime startDateTime = challenge.getStartAt()
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC);
        OffsetDateTime endDateTime = challenge.getEndAt()
                .plusDays(1)
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC);

        // Get sport types for this challenge
        Set<SportType> sportTypes = challenge.getSportTypeSet();
        Map<SportType, BigDecimal> goals = participant.getGoals();

        // Calculate progress for each sport
        int totalNormalizedPercent = 0;
        int sportCount = 0;

        LocalDate today = LocalDate.now();
        DailyProgress progress = progressRepository
                .findByChallengeIdAndUserIdAndDate(challenge.getId(), userId, today)
                .orElseGet(() -> DailyProgress.builder()
                        .challenge(challenge)
                        .user(participant.getUser())
                        .date(today)
                        .build());

        int totalDistanceAll = 0;

        for (SportType sportType : sportTypes) {
            String stravaSportType = mapSportTypeToStrava(sportType);
            
            // Sum distance for this sport type
            int distance = activityRepository.sumDistanceByUserAndSportTypeAndDateRange(
                    userId, stravaSportType, startDateTime, endDateTime);

            // Update per-sport distance in progress
            progress.setDistanceMeters(sportType, distance);
            totalDistanceAll += distance;

            // Calculate normalized percentage for this sport (capped at 100%)
            BigDecimal goalKm = goals.get(sportType);
            if (goalKm != null && goalKm.compareTo(BigDecimal.ZERO) > 0) {
                int goalMeters = goalKm.multiply(BigDecimal.valueOf(1000)).intValue();
                int percent = Math.min(100, (int) ((distance * 100L) / goalMeters));
                totalNormalizedPercent += percent;
                sportCount++;
                
                log.debug("Sport {}: {}m / {}m = {}%", sportType, distance, goalMeters, percent);
            }
        }

        // Calculate overall progress as average of all sports (per formula)
        int overallPercent = sportCount > 0 ? totalNormalizedPercent / sportCount : 0;

        // Update progress record
        progress.setDistanceMeters(totalDistanceAll); // Legacy total
        progress.setProgressPercent(overallPercent);
        progress.setUpdatedAt(Instant.now());
        progressRepository.save(progress);

        log.info("Updated multi-sport progress for user {} in challenge {}: overall {}% ({} sports)",
                userId, challenge.getId(), overallPercent, sportCount);
    }

    private String mapSportType(String stravaSportType) {
        if (stravaSportType == null) return "OTHER";
        return switch (stravaSportType.toLowerCase()) {
            case "run", "trailrun", "virtualrun" -> "Run";
            case "ride", "virtualride", "ebikeride", "mountainbikeride" -> "Ride";
            case "swim" -> "Swim";
            case "walk", "hike" -> "Walk";
            default -> stravaSportType;
        };
    }

    private String mapSportTypeToStrava(SportType sportType) {
        return switch (sportType) {
            case RUN -> "Run";
            case RIDE -> "Ride";
            case SWIM -> "Swim";
            case WALK -> "Walk";
        };
    }
}
