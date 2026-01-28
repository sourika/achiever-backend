package com.achiever.dto;

import jakarta.validation.constraints.Size;

public record UpdateChallengeRequest(
        @Size(max = 50) String name
) {}
