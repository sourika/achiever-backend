package com.achiever.dto;

import com.achiever.entity.ChallengeStatus;
import com.achiever.entity.SportType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ChallengeDTO(
        UUID id,
        String inviteCode,
        SportType sportType,
        LocalDate startAt,
        LocalDate endAt,
        ChallengeStatus status,
        Instant createdAt,
        UserDTO createdBy,
        List<ParticipantDTO> participants
) {}
