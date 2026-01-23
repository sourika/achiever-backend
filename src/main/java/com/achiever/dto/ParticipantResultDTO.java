package com.achiever.dto;

import java.util.UUID;

public record ParticipantResultDTO(
        UUID userId,
        String username,
        int percent
) {}
