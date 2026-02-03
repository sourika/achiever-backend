package com.achiever.dto;

import com.achiever.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationDTO(
        UUID id,
        NotificationType type,
        UUID challengeId,
        String challengeName,
        String message,
        boolean read,
        Instant createdAt
) {}