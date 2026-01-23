package com.achiever.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record JoinChallengeRequest(
        @NotNull @Positive BigDecimal goalValue
) {}
