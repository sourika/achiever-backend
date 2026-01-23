package com.achiever.service;

import com.achiever.entity.*;
import com.achiever.repository.*;
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

    /**
     * Activate challenges that should start today
     * Runs every hour
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void activateChallenges() {
        LocalDate today = LocalDate.now();
        
        List<Challenge> pendingChallenges = challengeRepository
                .findByStatusAndStartAtBefore(ChallengeStatus.PENDING, today.plusDays(1));

        for (Challenge challenge : pendingChallenges) {
            // Only activate if has 2 participants
            if (challenge.getParticipants().size() >= 2) {
                challenge.setStatus(ChallengeStatus.ACTIVE);
                challengeRepository.save(challenge);
                log.info("Activated challenge: {}", challenge.getId());
            }
        }
    }

    /**
     * Complete challenges that have ended
     * Runs every hour
     */
    @Scheduled(cron = "0 30 * * * *") // Every hour at :30
    @Transactional
    public void completeChallenges() {
        LocalDate today = LocalDate.now();
        
        List<Challenge> activeChallenges = challengeRepository
                .findByStatusAndEndAtBefore(ChallengeStatus.ACTIVE, today);

        for (Challenge challenge : activeChallenges) {
            challenge.setStatus(ChallengeStatus.COMPLETED);
            challengeRepository.save(challenge);
            
            // Calculate final results
            calculateFinalResults(challenge);
            
            log.info("Completed challenge: {}", challenge.getId());
        }
    }

    /**
     * Calculate weekly results
     * Runs every Monday at 00:05
     */
    @Scheduled(cron = "0 5 0 * * MON")
    @Transactional
    public void calculateWeeklyResults() {
        log.info("Starting weekly results calculation");
        
        LocalDate weekEnd = LocalDate.now().minusDays(1); // Yesterday (Sunday)
        LocalDate weekStart = weekEnd.minusDays(6); // Last Monday

        List<Challenge> activeChallenges = challengeRepository
                .findByStatus(ChallengeStatus.ACTIVE);

        for (Challenge challenge : activeChallenges) {
            // Skip if results already calculated for this week
            if (resultRepository.existsByChallengeIdAndWeekStart(challenge.getId(), weekStart)) {
                continue;
            }

            calculateWeekResult(challenge, weekStart);
        }
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

        // Get latest progress for each participant
        int percentA = getLatestProgressPercent(challenge.getId(), participantA.getUser().getId());
        int percentB = getLatestProgressPercent(challenge.getId(), participantB.getUser().getId());

        // Determine winner
        User winner = null;
        if (percentA > percentB) {
            winner = participantA.getUser();
        } else if (percentB > percentA) {
            winner = participantB.getUser();
        }
        // null winner = tie

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

        log.info("Week result for challenge {}: A={}%, B={}%, winner={}",
                challenge.getId(), percentA, percentB,
                winner != null ? winner.getId() : "TIE");
    }

    private void calculateFinalResults(Challenge challenge) {
        // For MVP, just calculate the final week results
        LocalDate weekStart = challenge.getStartAt();
        
        if (!resultRepository.existsByChallengeIdAndWeekStart(challenge.getId(), weekStart)) {
            calculateWeekResult(challenge, weekStart);
        }
    }

    private int getLatestProgressPercent(java.util.UUID challengeId, java.util.UUID userId) {
        return progressRepository
                .findByChallengeIdAndUserId(challengeId, userId).stream()
                .max((a, b) -> a.getDate().compareTo(b.getDate()))
                .map(DailyProgress::getProgressPercent)
                .orElse(0);
    }
}
