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
import java.util.Comparator;
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
    private final NotificationService notificationService;

    // ============================================================
    // DAILY MIDNIGHT JOB
    // ============================================================

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void midnightSync() {
        log.info("=== Starting midnight sync job ===");
        LocalDate today = LocalDate.now();

        processStatusTransitions(today);
        syncAllActiveChallenges();
        completeExpiredChallenges(today);

        log.info("=== Midnight sync job finished ===");
    }

    protected void processStatusTransitions(LocalDate today) {
        // PENDING → EXPIRED
        List<Challenge> pending = challengeRepository.findByStatus(ChallengeStatus.PENDING);
        for (Challenge challenge : pending) {
            if (challenge.getEndAt().isBefore(today)) {
                challenge.setStatus(ChallengeStatus.EXPIRED);
                challengeRepository.save(challenge);
                log.info("[CRON] Challenge {} '{}' expired (no opponent)",
                        challenge.getId(), challenge.getName());
                notificationService.notifyChallengeExpired(challenge);
            }
        }

        // SCHEDULED → ACTIVE
        List<Challenge> scheduled = challengeRepository.findByStatus(ChallengeStatus.SCHEDULED);
        for (Challenge challenge : scheduled) {
            if (!challenge.getStartAt().isAfter(today)) {
                challenge.setStatus(ChallengeStatus.ACTIVE);
                challengeRepository.save(challenge);
                log.info("[CRON] Challenge {} '{}' activated",
                        challenge.getId(), challenge.getName());
                notificationService.notifyChallengeStarted(challenge);
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

    protected void completeExpiredChallenges(LocalDate today) {
        List<Challenge> active = challengeRepository.findByStatus(ChallengeStatus.ACTIVE);

        for (Challenge challenge : active) {
            if (challenge.getEndAt().isBefore(today)) {
                syncChallengeParticipants(challenge);

                User winner = challengeService.determineWinner(challenge);
                challenge.setWinner(winner);
                challenge.setStatus(ChallengeStatus.COMPLETED);
                challengeRepository.save(challenge);

                log.info("[CRON] Challenge {} '{}' completed. Winner: {}",
                        challenge.getId(),
                        challenge.getName(),
                        winner != null ? winner.getUsername() : "TIE");

                notificationService.notifyChallengeCompleted(challenge, winner);
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
    // WEEKLY RESULTS
    // ============================================================

    @Scheduled(cron = "0 5 0 * * MON")
    @Transactional
    public void calculateWeeklyResults() {
        log.info("[CRON] Starting weekly results calculation");

        LocalDate weekEnd = LocalDate.now().minusDays(1);
        LocalDate weekStart = weekEnd.minusDays(6);

        List<Challenge> activeChallenges = challengeRepository.findByStatus(ChallengeStatus.ACTIVE);

        int calculated = 0;
        for (Challenge challenge : activeChallenges) {
            if (resultRepository.existsByChallengeIdAndWeekStart(challenge.getId(), weekStart)) {
                continue;
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
                .max(Comparator.comparing(DailyProgress::getDate))
                .map(DailyProgress::getProgressPercent)
                .orElse(0);
    }
}