package com.achiever.dto;

import com.achiever.entity.SportType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

public record JoinChallengeRequest(
        @NotNull @NotEmpty Map<SportType, @Positive BigDecimal> goals // sport -> goal in km
) {}
