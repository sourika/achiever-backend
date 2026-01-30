package com.achiever.service;

import com.achiever.dto.*;
import com.achiever.entity.*;
import com.achiever.repository.*;
import com.achiever.strava.StravaSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChallengeService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final DailyProgressRepository progressRepository;
    private final StravaSyncService stravaSyncService;

    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final SecureRandom random = new SecureRandom();

    /**
     * Create a new challenge with multiple sports
     */
    @Transactional
    public ChallengeDTO createChallenge(User creator, CreateChallengeRequest request) {
        // Get current date in user's timezone (default to UTC if not provided)
        ZoneId zoneId = ZoneId.of("UTC");
        if (request.timezone() != null && !request.timezone().isEmpty()) {
            try {
                zoneId = ZoneId.of(request.timezone());
            } catch (Exception e) {
                // Invalid timezone, use UTC
            }
        }
        LocalDate today = LocalDate.now(zoneId);

        // Validate start date is not in the past
        if (request.startAt().isBefore(today)) {
            throw new IllegalArgumentException("Start date cannot be in the past");
        }

        // Validate dates - end must be after start
        if (request.endAt().isBefore(request.startAt()) ||
                request.endAt().isEqual(request.startAt())) {
            throw new IllegalArgumentException("End date must be after start date");
        }

        // Validate goals - at least one sport must be selected
        if (request.goals() == null || request.goals().isEmpty()) {
            throw new IllegalArgumentException("At least one sport goal must be specified");
        }

        // Generate unique invite code
        String inviteCode = generateUniqueInviteCode();

        // Get sport types from goals
        Set<SportType> sportTypes = request.goals().keySet();

        Challenge challenge = Challenge.builder()
                .createdBy(creator)
                .name(request.name())
                .inviteCode(inviteCode)
                .startAt(request.startAt())
                .endAt(request.endAt())
                .status(ChallengeStatus.PENDING)
                .build();
        challenge.setSportTypeSet(sportTypes);

        challenge = challengeRepository.save(challenge);

        // Add creator as first participant with goals
        ChallengeParticipant participant = ChallengeParticipant.builder()
                .challenge(challenge)
                .user(creator)
                .build();
        participant.setGoals(request.goals());

        participantRepository.save(participant);
        challenge.getParticipants().add(participant);

        log.info("Challenge created: {} by user {} with sports: {}",
                challenge.getId(), creator.getId(), sportTypes);

        return mapToDTO(challenge);
    }

    @Transactional
    public ChallengeDTO updateChallenge(UUID challengeId, UpdateChallengeRequest request, User user) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        // Only creator can update
        if (!challenge.getCreatedBy().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Only the creator can update this challenge");
        }

        if (request.name() != null) {
            challenge.setName(request.name());
        }

        challenge = challengeRepository.save(challenge);
        return mapToDTO(challenge);
    }

    @Transactional
    public void deleteChallenge(UUID challengeId, User user) {
        Challenge challenge = challengeRepository.findByIdWithParticipants(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        // Only creator can delete
        if (!challenge.getCreatedBy().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Only the creator can delete this challenge");
        }

        // Cannot delete ACTIVE challenge
        if (challenge.getStatus() == ChallengeStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot delete active challenge");
        }

        // Cannot delete SCHEDULED if opponent joined (MVP: be fair to opponent)
        if (challenge.getStatus() == ChallengeStatus.SCHEDULED && challenge.getParticipants().size() > 1) {
            throw new IllegalArgumentException("Cannot delete: opponent already joined. Ask them to leave first.");
        }

        // Cannot delete if creator has forfeited
        boolean creatorForfeited = challenge.getParticipants().stream()
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .anyMatch(ChallengeParticipant::hasForfeited);
        if (creatorForfeited) {
            throw new IllegalArgumentException("Cannot delete: you have forfeited this challenge");
        }

        challengeRepository.delete(challenge);
        log.info("Challenge {} deleted by user {}", challengeId, user.getId());
    }

    /**
     * Join challenge by invite code
     */
    @Transactional
    public ChallengeDTO joinChallenge(User user, String inviteCode, JoinChallengeRequest request) {
        Challenge challenge = challengeRepository.findByInviteCodeWithParticipants(inviteCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite code"));

        // Check if already joined
        if (participantRepository.existsByChallengeIdAndUserId(challenge.getId(), user.getId())) {
            throw new IllegalStateException("Already joined this challenge");
        }

        // Check participant limit (MVP: 2 participants)
        if (challenge.getParticipants().size() >= 2) {
            throw new IllegalStateException("Challenge is full");
        }

        // Check if challenge hasn't ended
        LocalDate today = getTodayInCreatorTimezone(challenge);
        if (challenge.getEndAt().isBefore(today)) {
            throw new IllegalStateException("Challenge has ended");
        }

        // Add opponent's sports to challenge (merge with existing)
        Set<SportType> challengeSports = challenge.getSportTypeSet();
        Set<SportType> goalSports = request.goals().keySet();

        // Validate at least one sport selected
        if (goalSports.isEmpty()) {
            throw new IllegalArgumentException("At least one sport must be selected");
        }

        // Merge opponent's sports with existing challenge sports
        Set<SportType> allSports = new HashSet<>(challengeSports);
        allSports.addAll(goalSports);
        challenge.setSportTypeSet(allSports);

        ChallengeParticipant participant = ChallengeParticipant.builder()
                .challenge(challenge)
                .user(user)
                .build();
        participant.setGoals(request.goals());

        participantRepository.save(participant);
        challenge.getParticipants().add(participant);

        log.info("User {} joined challenge {}", user.getId(), challenge.getId());

        // Update status when second participant joins
        if (challenge.getParticipants().size() >= 2 && challenge.getStatus() == ChallengeStatus.PENDING) {
            if (challenge.getStartAt().isAfter(today)) {
                challenge.setStatus(ChallengeStatus.SCHEDULED);
            } else {
                challenge.setStatus(ChallengeStatus.ACTIVE);
            }
            challengeRepository.save(challenge);
        }

        // Sync Strava data for joining user if challenge is already active
        if (challenge.getStatus() == ChallengeStatus.ACTIVE && user.getStravaConnection() != null) {
            try {
                // Sync exactly for challenge period: startAt → today
                stravaSyncService.syncActivitiesForDateRange(
                        user.getId(),
                        challenge.getStartAt(),
                        today
                );
                log.info("Synced Strava for joining user {} from {} to {}",
                        user.getUsername(), challenge.getStartAt(), today);
            } catch (Exception e) {
                log.warn("Failed to sync Strava for joining user {}: {}", user.getUsername(), e.getMessage());
            }
        }

        return mapToDTO(challenge);
    }

    /**
     * Leave (or forfeit) a challenge
     */
    @Transactional
    public ChallengeDTO leaveChallenge(UUID challengeId, User user) {
        Challenge challenge = challengeRepository.findByIdWithParticipants(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        boolean isCreator = challenge.getCreatedBy().getId().equals(user.getId());

        // Find participant
        ChallengeParticipant participant = challenge.getParticipants().stream()
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("You are not in this challenge"));

        if (challenge.getStatus() == ChallengeStatus.SCHEDULED) {
            // SCHEDULED: Creator cannot leave, only delete
            if (isCreator) {
                throw new IllegalArgumentException("Creator cannot leave scheduled challenge. Use delete instead.");
            }
            // Remove participant completely, revert to PENDING
            challenge.getParticipants().remove(participant);
            participantRepository.delete(participant);
            challenge.setStatus(ChallengeStatus.PENDING);
            challengeRepository.save(challenge);
            log.info("User {} left scheduled challenge {}, status reverted to PENDING", user.getId(), challengeId);
        } else if (challenge.getStatus() == ChallengeStatus.ACTIVE) {
            // ACTIVE: Both creator and opponent can forfeit
            if (participant.hasForfeited()) {
                throw new IllegalStateException("Already left this challenge");
            }
            participant.setForfeitedAt(LocalDateTime.now());
            participantRepository.save(participant);
            log.info("User {} forfeited active challenge {}", user.getId(), challengeId);
        } else {
            throw new IllegalStateException("Cannot leave challenge with status: " + challenge.getStatus());
        }

        return mapToDTO(challenge);
    }

    /**
     * Finish challenge early (claim win after opponent forfeited)
     */
    @Transactional
    public ChallengeDTO finishChallenge(UUID challengeId, User user) {
        Challenge challenge = challengeRepository.findByIdWithParticipants(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        // Check if user is participant
        boolean isParticipant = challenge.getParticipants().stream()
                .anyMatch(p -> p.getUser().getId().equals(user.getId()));
        if (!isParticipant) {
            throw new IllegalArgumentException("You are not in this challenge");
        }

        // Check if current user has forfeited
        boolean currentUserForfeited = challenge.getParticipants().stream()
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .anyMatch(ChallengeParticipant::hasForfeited);
        if (currentUserForfeited) {
            throw new IllegalStateException("You have forfeited this challenge");
        }

        // Check if opponent has forfeited
        boolean opponentForfeited = challenge.getParticipants().stream()
                .filter(p -> !p.getUser().getId().equals(user.getId()))
                .anyMatch(ChallengeParticipant::hasForfeited);

        if (!opponentForfeited) {
            throw new IllegalStateException("Cannot finish: opponent has not left");
        }

        // Mark challenge as completed with user as winner
        challenge.setStatus(ChallengeStatus.COMPLETED);
        challenge.setWinner(user);
        challengeRepository.save(challenge);

        log.info("Challenge {} finished early by user {}, winner set", challengeId, user.getId());

        return mapToDTO(challenge);
    }

    /**
     * Get challenge by ID with lazy status update and Strava sync
     */
    @Transactional
    public ChallengeDTO getChallenge(UUID challengeId) {
        Challenge challenge = challengeRepository.findByIdWithParticipants(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        // Lazy status update
        updateStatusIfNeeded(challenge);

        /// Disabled for MVP - use manual sync button instead
        // Set<UUID> syncedUserIds = new HashSet<>();
        // syncStravaForParticipants(challenge, syncedUserIds);

        return mapToDTO(challenge);
    }

    /**
     * Get challenge by invite code (public endpoint for preview)
     */
    @Transactional(readOnly = true)
    public ChallengeDTO getChallengeByInviteCode(String inviteCode) {
        Challenge challenge = challengeRepository.findByInviteCodeWithParticipants(inviteCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite code"));
        return mapToDTO(challenge);
    }

    /**
     * Get current progress for a challenge with lazy sync
     */
    @Transactional
    public ChallengeProgressDTO getChallengeProgress(UUID challengeId) {
        Challenge challenge = challengeRepository.findByIdWithParticipants(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        // Lazy status update
        updateStatusIfNeeded(challenge);

        // Lazy Strava sync
        Set<UUID> syncedUserIds = new HashSet<>();
        syncStravaForParticipants(challenge, syncedUserIds);

        List<DailyProgress> currentProgress = progressRepository
                .findCurrentProgressByChallengeId(challengeId);

        Set<SportType> challengeSports = challenge.getSportTypeSet();

        List<ParticipantProgressDTO> participantProgress = challenge.getParticipants().stream()
                .map(p -> {
                    DailyProgress progress = currentProgress.stream()
                            .filter(dp -> dp.getUser().getId().equals(p.getUser().getId()))
                            .findFirst()
                            .orElse(null);

                    Map<SportType, BigDecimal> goals = p.getGoals();
                    Map<SportType, Integer> distances = new HashMap<>();
                    Map<SportType, Integer> sportPercents = new HashMap<>();

                    // Calculate progress for each sport that participant selected
                    for (SportType sport : goals.keySet()) {
                        int distance = progress != null ? progress.getDistanceMeters(sport) : 0;
                        distances.put(sport, distance);

                        BigDecimal goalKm = goals.get(sport);
                        int percent = 0;
                        if (goalKm != null && goalKm.compareTo(BigDecimal.ZERO) > 0) {
                            int goalMeters = goalKm.multiply(BigDecimal.valueOf(1000)).intValue();
                            percent = Math.min(100, (int) ((distance * 100L) / goalMeters));
                        }
                        sportPercents.put(sport, percent);
                    }

                    // Calculate overall progress as average of all sports (capped at 100 each)
                    int overallPercent = sportPercents.isEmpty() ? 0 :
                            (int) sportPercents.values().stream()
                                    .mapToInt(Integer::intValue)
                                    .average()
                                    .orElse(0);

                    return new ParticipantProgressDTO(
                            p.getUser().getId(),
                            p.getUser().getUsername(),
                            goals,
                            distances,
                            sportPercents,
                            overallPercent
                    );
                })
                .toList();

        // Calculate time remaining
        long timeRemaining = calculateTimeRemaining(challenge);

        return new ChallengeProgressDTO(
                challenge.getId(),
                challenge.getStatus(),
                challengeSports,
                challenge.getStartAt(),
                challenge.getEndAt(),
                timeRemaining,
                participantProgress
        );
    }

    /**
     * Get user's challenges with lazy status update and Strava sync
     */
    @Transactional
    public List<ChallengeDTO> getUserChallenges(UUID userId) {
        List<Challenge> challenges = challengeRepository.findByParticipantUserId(userId);

        Set<UUID> syncedUserIds = new HashSet<>();

        for (Challenge challenge : challenges) {
            // Lazy status update
            updateStatusIfNeeded(challenge);

            // Lazy Strava sync for active challenges
            // Disabled for MVP - use manual sync button instead
            // syncStravaForParticipants(challenge, syncedUserIds);
        }

        return challenges.stream()
                .map(this::mapToDTO)
                .toList();
    }

    /**
     * Get user's active challenges
     */
    @Transactional(readOnly = true)
    public List<ChallengeDTO> getUserActiveChallenges(UUID userId) {
        return challengeRepository
                .findByParticipantUserIdAndStatus(userId, ChallengeStatus.ACTIVE).stream()
                .map(this::mapToDTO)
                .toList();
    }

    /**
     * Get current date in creator's timezone
     */
    private LocalDate getTodayInCreatorTimezone(Challenge challenge) {
        ZoneId zoneId = ZoneId.of("UTC");
        String creatorTimezone = challenge.getCreatedBy().getTimezone();
        if (creatorTimezone != null && !creatorTimezone.isEmpty()) {
            try {
                zoneId = ZoneId.of(creatorTimezone);
            } catch (Exception e) {
                log.warn("Invalid timezone '{}', using UTC", creatorTimezone);
            }
        }
        return LocalDate.now(zoneId);
    }

    /**
     * Update challenge status based on dates (lazy evaluation)
     */
    private void updateStatusIfNeeded(Challenge challenge) {
        LocalDate today = getTodayInCreatorTimezone(challenge);
        ChallengeStatus currentStatus = challenge.getStatus();
        boolean changed = false;

        // PENDING -> EXPIRED (end date passed without opponent joining)
        if (currentStatus == ChallengeStatus.PENDING && challenge.getEndAt().isBefore(today)) {
            challenge.setStatus(ChallengeStatus.EXPIRED);
            changed = true;
            log.info("Challenge {} expired (no opponent joined)", challenge.getId());
        }

        // SCHEDULED -> ACTIVE (start date reached)
        if (currentStatus == ChallengeStatus.SCHEDULED && !challenge.getStartAt().isAfter(today)) {
            challenge.setStatus(ChallengeStatus.ACTIVE);
            changed = true;
            log.info("Challenge {} activated (lazy)", challenge.getId());
        }

        // ACTIVE -> COMPLETED (end date passed)
        if (challenge.getStatus() == ChallengeStatus.ACTIVE && challenge.getEndAt().isBefore(today)) {
            challenge.setStatus(ChallengeStatus.COMPLETED);

            // Determine winner
            User winner = determineWinner(challenge);
            challenge.setWinner(winner);

            changed = true;
            log.info("Challenge {} completed (lazy), winner: {}",
                    challenge.getId(),
                    winner != null ? winner.getUsername() : "tie");
        }

        if (changed) {
            challengeRepository.save(challenge);
        }
    }

    /**
     * Sync Strava data for all participants in a challenge
     */
    private void syncStravaForParticipants(Challenge challenge, Set<UUID> alreadySynced) {
        // Only sync for active challenges
        if (challenge.getStatus() != ChallengeStatus.ACTIVE) {
            return;
        }

        LocalDate today = getTodayInCreatorTimezone(challenge);

        for (ChallengeParticipant participant : challenge.getParticipants()) {
            User user = participant.getUser();

            // Skip if already synced this session
            if (alreadySynced.contains(user.getId())) {
                continue;
            }

            // Skip if no Strava connection
            if (user.getStravaConnection() == null) {
                continue;
            }

            // Skip if forfeited
            if (participant.hasForfeited()) {
                continue;
            }

            try {
                // Sync exactly for challenge period: startAt → today
                stravaSyncService.syncActivitiesForDateRange(
                        user.getId(),
                        challenge.getStartAt(),
                        today
                );
                alreadySynced.add(user.getId());
                log.debug("Synced Strava for user {} (lazy) from {} to {}",
                        user.getUsername(), challenge.getStartAt(), today);
            } catch (Exception e) {
                log.warn("Failed to sync Strava for user {}: {}", user.getUsername(), e.getMessage());
            }
        }
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            code = generateInviteCode();
        } while (challengeRepository.findByInviteCode(code).isPresent());
        return code;
    }

    private String generateInviteCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(random.nextInt(INVITE_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private long calculateTimeRemaining(Challenge challenge) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = challenge.getEndAt().plusDays(1).atStartOfDay();

        if (now.isAfter(end)) {
            return 0;
        }

        return Duration.between(now, end).getSeconds();
    }

    /**
     * Determine the winner of a completed challenge
     * Returns null if tie
     */
    User determineWinner(Challenge challenge) {
        List<ChallengeParticipant> activeParticipants = challenge.getParticipants().stream()
                .filter(p -> !p.hasForfeited())
                .toList();

        // If only one active participant (opponent forfeited), they win
        if (activeParticipants.size() == 1) {
            return activeParticipants.get(0).getUser();
        }

        // If no active participants, no winner
        if (activeParticipants.isEmpty()) {
            return null;
        }

        // Calculate overall progress for each participant
        User winner = null;
        int maxPercent = -1;
        boolean isTie = false;

        for (ChallengeParticipant participant : activeParticipants) {
            int overallPercent = calculateOverallPercent(challenge, participant);

            if (overallPercent > maxPercent) {
                maxPercent = overallPercent;
                winner = participant.getUser();
                isTie = false;
            } else if (overallPercent == maxPercent) {
                isTie = true;
            }
        }

        return isTie ? null : winner;
    }

    /**
     * Calculate overall progress percent for a participant
     */
    private int calculateOverallPercent(Challenge challenge, ChallengeParticipant participant) {
        DailyProgress progress = progressRepository
                .findLatestByChallengeIdAndUserId(challenge.getId(), participant.getUser().getId())
                .orElse(null);

        if (progress == null) {
            return 0;
        }

        Map<SportType, BigDecimal> goals = participant.getGoals();
        if (goals.isEmpty()) {
            return 0;
        }

        int totalPercent = 0;
        int sportCount = 0;

        for (Map.Entry<SportType, BigDecimal> entry : goals.entrySet()) {
            SportType sport = entry.getKey();
            BigDecimal goalKm = entry.getValue();

            if (goalKm != null && goalKm.compareTo(BigDecimal.ZERO) > 0) {
                int distance = progress.getDistanceMeters(sport);
                int goalMeters = goalKm.multiply(BigDecimal.valueOf(1000)).intValue();
                int percent = Math.min(100, (int) ((distance * 100L) / goalMeters));
                totalPercent += percent;
                sportCount++;
            }
        }

        return sportCount > 0 ? totalPercent / sportCount : 0;
    }

    private ChallengeDTO mapToDTO(Challenge challenge) {
        List<ParticipantDTO> participants = challenge.getParticipants().stream()
                .map(p -> new ParticipantDTO(
                        p.getUser().getId(),
                        p.getUser().getUsername(),
                        p.getGoals(),
                        p.getJoinedAt(),
                        p.getForfeitedAt()
                ))
                .toList();

        return new ChallengeDTO(
                challenge.getId(),
                challenge.getInviteCode(),
                challenge.getSportTypeSet(),
                challenge.getStartAt(),
                challenge.getEndAt(),
                challenge.getName(),
                challenge.getStatus(),
                challenge.getCreatedAt(),
                new UserDTO(
                        challenge.getCreatedBy().getId(),
                        challenge.getCreatedBy().getUsername(),
                        challenge.getCreatedBy().getEmail(),
                        challenge.getCreatedBy().getTimezone(),
                        challenge.getCreatedBy().getStravaConnection() != null,
                        challenge.getCreatedBy().getPasswordHash() != null
                ),
                participants,
                challenge.getWinner() != null ? challenge.getWinner().getId() : null
        );
    }
}