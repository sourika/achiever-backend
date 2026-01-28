package com.achiever.dto;

import com.achiever.entity.ChallengeStatus;
import com.achiever.entity.SportType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ChallengeDTO(
        UUID id,
        String inviteCode,
        Set<SportType> sportTypes,
        LocalDate startAt,
        LocalDate endAt,
        String name,
        ChallengeStatus status,
        Instant createdAt,
        UserDTO createdBy,
        List<ParticipantDTO> participants
) {}
