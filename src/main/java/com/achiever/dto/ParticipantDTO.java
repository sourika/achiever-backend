package com.achiever.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ParticipantDTO(
        UUID userId,
        String username,
        BigDecimal goalValue,
        Instant joinedAt
) {}
