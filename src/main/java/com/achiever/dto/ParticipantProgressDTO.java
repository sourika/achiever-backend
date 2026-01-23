package com.achiever.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ParticipantProgressDTO(
        UUID userId,
        String username,
        BigDecimal goalKm,
        int currentDistanceMeters,
        int progressPercent
) {}
