package com.achiever.dto;

import com.achiever.entity.SportType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record CreateChallengeRequest(
        @NotNull @NotEmpty Map<SportType, @Positive BigDecimal> goals, // sport -> goal in km
        @NotNull @FutureOrPresent LocalDate startAt,
        @NotNull @Future LocalDate endAt
) {}
