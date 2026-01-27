package com.achiever.dto;

import com.achiever.entity.SportType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ParticipantDTO(
        UUID userId,
        String username,
        Map<SportType, BigDecimal> goals, // sport -> goal in km
        Instant joinedAt
) {}
