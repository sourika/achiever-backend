package com.achiever.dto;

public record AuthResponse(
        String token,
        UserDTO user
) {}