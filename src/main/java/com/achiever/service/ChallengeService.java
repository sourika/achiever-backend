package com.achiever.service;

import com.achiever.dto.*;
import com.achiever.entity.*;
import com.achiever.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChallengeService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final DailyProgressRepository progressRepository;
    private final UserRepository userRepository;

    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final SecureRandom random = new SecureRandom();

    /**
     * Create a new challenge
     */
    @Transactional
    public ChallengeDTO createChallenge(User creator, CreateChallengeRequest request) {
        // Validate dates
        if (request.endAt().isBefore(request.startAt()) || 
            request.endAt().isEqual(request.startAt())) {
            throw new IllegalArgumentException("End date must be after start date");
        }

        // Generate unique invite code
        String inviteCode = generateUniqueInviteCode();

        Challenge challenge = Challenge.builder()
                .createdBy(creator)
                .inviteCode(inviteCode)
                .sportType(request.sportType())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .status(ChallengeStatus.PENDING)
                .build();

        challenge = challengeRepository.save(challenge);

        // Add creator as first participant
        ChallengeParticipant participant = ChallengeParticipant.builder()
                .challenge(challenge)
                .user(creator)
                .goalValue(request.goalValue())
                .build();

        participantRepository.save(participant);
        challenge.getParticipants().add(participant);

        log.info("Challenge created: {} by user {}", challenge.getId(), creator.getId());

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

        ChallengeParticipant participant = ChallengeParticipant.builder()
                .challenge(challenge)
                .user(user)
                .goalValue(request.goalValue())
                .build();

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

        List<ParticipantProgressDTO> participantProgress = challenge.getParticipants().stream()
                .map(p -> {
                    DailyProgress progress = currentProgress.stream()
                            .filter(dp -> dp.getUser().getId().equals(p.getUser().getId()))
                            .findFirst()
                            .orElse(null);

                    return new ParticipantProgressDTO(
                            p.getUser().getId(),
                            p.getUser().getUsername(),
                            p.getGoalValue(),
                            progress != null ? progress.getDistanceMeters() : 0,
                            progress != null ? progress.getProgressPercent() : 0
                    );
                })
                .toList();

        // Calculate time remaining
        long timeRemaining = calculateTimeRemaining(challenge);

        return new ChallengeProgressDTO(
                challenge.getId(),
                challenge.getStatus(),
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
                        p.getGoalValue(),
                        p.getJoinedAt()
                ))
                .toList();

        return new ChallengeDTO(
                challenge.getId(),
                challenge.getInviteCode(),
                challenge.getSportType(),
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
