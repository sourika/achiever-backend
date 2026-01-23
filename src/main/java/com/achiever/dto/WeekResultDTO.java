package com.achiever.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WeekResultDTO(
        UUID id,
        LocalDate weekStart,
        ParticipantResultDTO userA,
        ParticipantResultDTO userB,
        UUID winnerId,
        Instant computedAt
) {}
