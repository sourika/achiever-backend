package com.achiever.dto;

import com.achiever.entity.SportType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record CreateChallengeRequest(
        @NotNull @NotEmpty Map<SportType, @Positive BigDecimal> goals,
        @NotNull LocalDate startAt,
        @NotNull LocalDate endAt
) {}