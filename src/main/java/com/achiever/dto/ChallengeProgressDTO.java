package com.achiever.dto;

import com.achiever.entity.ChallengeStatus;
import com.achiever.entity.SportType;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ChallengeProgressDTO(
        UUID challengeId,
        ChallengeStatus status,
        Set<SportType> sportTypes,
        LocalDate startAt,
        LocalDate endAt,
        long timeRemainingSeconds,
        List<ParticipantProgressDTO> participants
) {}
