package com.achiever.dto;

import com.achiever.entity.SportType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record ParticipantProgressDTO(
        UUID userId,
        String username,
        Map<SportType, BigDecimal> goals, // sport -> goal in km
        Map<SportType, Integer> currentDistances, // sport -> current meters
        Map<SportType, Integer> sportProgressPercents, // sport -> progress percent (0-100, capped)
        int overallProgressPercent // weighted average of all sports
) {}
