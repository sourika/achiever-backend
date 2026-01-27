package com.achiever.dto;

import com.achiever.entity.User;
import java.util.UUID;

public record UserDTO(
        UUID id,
        String username,
        String email,
        String timezone,
        boolean stravaConnected,
        boolean hasPassword
) {
    public static UserDTO fromUser(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getTimezone(),
                user.getStravaConnection() != null,
                user.getPasswordHash() != null
        );
    }
}