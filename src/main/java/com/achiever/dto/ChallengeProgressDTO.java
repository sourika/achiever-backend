package com.achiever.dto;

import com.achiever.entity.ChallengeStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ChallengeProgressDTO(
        UUID challengeId,
        ChallengeStatus status,
        LocalDate startAt,
        LocalDate endAt,
        long timeRemainingSeconds,
        List<ParticipantProgressDTO> participants
) {}
