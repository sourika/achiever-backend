package com.achiever.service;

import com.achiever.entity.*;
import com.achiever.repository.*;
import com.achiever.strava.StravaSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChallengeSchedulerService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final DailyProgressRepository progressRepository;
    private final ChallengeWeekResultRepository resultRepository;
    private final ChallengeService challengeService;
    private final StravaSyncService stravaSyncService;

    // ============================================================
    // DAILY MIDNIGHT JOB
    // ============================================================

    /**
     * Runs every day at midnight UTC.
     * Handles all daily maintenance tasks.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void midnightSync() {
        log.info("=== Starting midnight sync job ===");
        LocalDate today = LocalDate.now();

        // 1. Status transitions (idempotent - safe to run even if lazy already did it)
        processStatusTransitions(today);

        // 2. Sync Strava for all ACTIVE challenges
        syncAllActiveChallenges();

        // 3. Complete challenges and determine winners
        completeExpiredChallenges(today);

        log.info("=== Midnight sync job finished ===");
    }

    @Transactional
    protected void processStatusTransitions(LocalDate today) {
        // PENDING → EXPIRED (no opponent joined before end date)
        List<Challenge> pending = challengeRepository.findByStatus(ChallengeStatus.PENDING);
        for (Challenge challenge : pending) {
            if (challenge.getEndAt().isBefore(today)) {
                challenge.setStatus(ChallengeStatus.EXPIRED);
                challengeRepository.save(challenge);
                log.info("[CRON] Challenge {} '{}' expired (no opponent)",
                        challenge.getId(), challenge.getName());
            }
        }

        // SCHEDULED → ACTIVE (start date reached)
        List<Challenge> scheduled = challengeRepository.findByStatus(ChallengeStatus.SCHEDULED);
        for (Challenge challenge : scheduled) {
            if (!challenge.getStartAt().isAfter(today)) {
                challenge.setStatus(ChallengeStatus.ACTIVE);
                challengeRepository.save(challenge);
                log.info("[CRON] Challenge {} '{}' activated",
                        challenge.getId(), challenge.getName());
                // TODO: Send push notification "Challenge started! 🏃"
            }
        }
    }

    protected void syncAllActiveChallenges() {
        List<Challenge> active = challengeRepository.findByStatus(ChallengeStatus.ACTIVE);
        log.info("[CRON] Syncing {} active challenges", active.size());

        for (Challenge challenge : active) {
            syncChallengeParticipants(challenge);
        }
    }

    @Transactional
    protected void completeExpiredChallenges(LocalDate today) {
        List<Challenge> active = challengeRepository.findByStatus(ChallengeStatus.ACTIVE);

        for (Challenge challenge : active) {
            if (challenge.getEndAt().isBefore(today)) {
                // Final sync to get latest data
                syncChallengeParticipants(challenge);

                // Determine winner
                User winner = challengeService.determineWinner(challenge);
                challenge.setWinner(winner);
                challenge.setStatus(ChallengeStatus.COMPLETED);
                challengeRepository.save(challenge);

                log.info("[CRON] Challenge {} '{}' completed. Winner: {}",
                        challenge.getId(),
                        challenge.getName(),
                        winner != null ? winner.getUsername() : "TIE");

                // TODO: Send push notification "🏆 You won!" / "Challenge ended"
            }
        }
    }

    private void syncChallengeParticipants(Challenge challenge) {
        for (ChallengeParticipant participant : challenge.getParticipants()) {
            if (participant.hasForfeited()) {
                continue;
            }

            User user = participant.getUser();
            if (user.getStravaConnection() == null) {
                continue;
            }

            try {
                stravaSyncService.syncAndUpdateProgress(user, challenge);
                log.debug("[CRON] Synced user {} in challenge {}",
                        user.getUsername(), challenge.getId());
            } catch (Exception e) {
                log.warn("[CRON] Sync failed for user {} in challenge {}: {}",
                        user.getUsername(), challenge.getId(), e.getMessage());
            }
        }
    }

    // ============================================================
    // WEEKLY RESULTS (for multi-week challenges)
    // ============================================================

    /**
     * Calculate weekly results for long-running challenges.
     * Runs every Monday at 00:05 UTC.
     */
    @Scheduled(cron = "0 5 0 * * MON")
    @Transactional
    public void calculateWeeklyResults() {
        log.info("[CRON] Starting weekly results calculation");

        LocalDate weekEnd = LocalDate.now().minusDays(1); // Yesterday (Sunday)
        LocalDate weekStart = weekEnd.minusDays(6); // Last Monday

        List<Challenge> activeChallenges = challengeRepository.findByStatus(ChallengeStatus.ACTIVE);

        int calculated = 0;
        for (Challenge challenge : activeChallenges) {
            if (resultRepository.existsByChallengeIdAndWeekStart(challenge.getId(), weekStart)) {
                continue; // Already calculated
            }
            calculateWeekResult(challenge, weekStart);
            calculated++;
        }

        log.info("[CRON] Weekly results calculated for {} challenges", calculated);
    }

    private void calculateWeekResult(Challenge challenge, LocalDate weekStart) {
        List<ChallengeParticipant> participants = participantRepository
                .findByChallengeId(challenge.getId());

        if (participants.size() != 2) {
            log.warn("Challenge {} doesn't have exactly 2 participants", challenge.getId());
            return;
        }

        ChallengeParticipant participantA = participants.get(0);
        ChallengeParticipant participantB = participants.get(1);

        int percentA = getLatestProgressPercent(challenge.getId(), participantA.getUser().getId());
        int percentB = getLatestProgressPercent(challenge.getId(), participantB.getUser().getId());

        User winner = null;
        if (percentA > percentB) {
            winner = participantA.getUser();
        } else if (percentB > percentA) {
            winner = participantB.getUser();
        }

        ChallengeWeekResult result = ChallengeWeekResult.builder()
                .challenge(challenge)
                .weekStart(weekStart)
                .userA(participantA.getUser())
                .userB(participantB.getUser())
                .userAPercent(percentA)
                .userBPercent(percentB)
                .winner(winner)
                .build();

        resultRepository.save(result);

        log.info("[CRON] Week result for challenge {}: A={}%, B={}%, winner={}",
                challenge.getId(), percentA, percentB,
                winner != null ? winner.getUsername() : "TIE");
    }

    private int getLatestProgressPercent(java.util.UUID challengeId, java.util.UUID userId) {
        return progressRepository
                .findByChallengeIdAndUserId(challengeId, userId).stream()
                .max((a, b) -> a.getDate().compareTo(b.getDate()))
                .map(DailyProgress::getProgressPercent)
                .orElse(0);
    }

    // ============================================================
    // FUTURE: Webhook handler (will be called from controller)
    // ============================================================

    /**
     * Handle incoming Strava webhook event.
     * Called when Strava sends activity create/update/delete event.
     *
     * @param stravaUserId Strava athlete ID
     * @param activityId Strava activity ID
     * @param aspectType "create", "update", or "delete"
     */
    // public void handleStravaWebhook(Long stravaUserId, Long activityId, String aspectType) {
    //     log.info("[WEBHOOK] Received {} for activity {} from athlete {}",
    //         aspectType, activityId, stravaUserId);
    //
    //     // 1. Find user by stravaUserId
    //     // 2. Fetch activity details from Strava API
    //     // 3. Update progress for all ACTIVE challenges of this user
    //     // 4. No status changes here - that's lazy/cron responsibility
    // }
}