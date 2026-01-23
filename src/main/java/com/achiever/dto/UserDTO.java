package com.achiever.dto;

import java.util.UUID;

public record UserDTO(
        UUID id,
        String username,
        String email,
        String timezone,
        boolean stravaConnected
) {}
