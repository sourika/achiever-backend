package com.achiever.service;

import com.achiever.dto.*;
import com.achiever.entity.*;
import com.achiever.repository.*;
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
        if (challenge.getEndAt().isBefore(LocalDate.now())) {
            throw new IllegalStateException("Challenge has ended");
        }

        // Validate that goals match challenge sport types
        Set<SportType> challengeSports = challenge.getSportTypeSet();
        Set<SportType> goalSports = request.goals().keySet();
        
        if (!goalSports.equals(challengeSports)) {
            throw new IllegalArgumentException(
                "Goals must be provided for all challenge sports: " + challengeSports);
        }

        ChallengeParticipant participant = ChallengeParticipant.builder()
                .challenge(challenge)
                .user(user)
                .build();
        participant.setGoals(request.goals());

        participantRepository.save(participant);
        challenge.getParticipants().add(participant);

        log.info("User {} joined challenge {}", user.getId(), challenge.getId());

        return mapToDTO(challenge);
    }

    /**
     * Get challenge by ID
     */
    @Transactional(readOnly = true)
    public ChallengeDTO getChallenge(UUID challengeId) {
        Challenge challenge = challengeRepository.findByIdWithParticipants(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));
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
     * Get current progress for a challenge
     */
    @Transactional(readOnly = true)
    public ChallengeProgressDTO getChallengeProgress(UUID challengeId) {
        Challenge challenge = challengeRepository.findByIdWithParticipants(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

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

                    // Calculate progress for each sport
                    for (SportType sport : challengeSports) {
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
     * Get user's challenges
     */
    @Transactional(readOnly = true)
    public List<ChallengeDTO> getUserChallenges(UUID userId) {
        return challengeRepository.findByParticipantUserId(userId).stream()
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

    private ChallengeDTO mapToDTO(Challenge challenge) {
        List<ParticipantDTO> participants = challenge.getParticipants().stream()
                .map(p -> new ParticipantDTO(
                        p.getUser().getId(),
                        p.getUser().getUsername(),
                        p.getGoals(),
                        p.getJoinedAt()
                ))
                .toList();

        return new ChallengeDTO(
                challenge.getId(),
                challenge.getInviteCode(),
                challenge.getSportTypeSet(),
                challenge.getStartAt(),
                challenge.getEndAt(),
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
                participants
        );
    }
}
