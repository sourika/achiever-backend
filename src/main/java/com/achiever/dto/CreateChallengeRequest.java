package com.achiever.dto;

import com.achiever.entity.SportType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateChallengeRequest(
        @NotNull SportType sportType,
        @NotNull @Positive BigDecimal goalValue,
        @NotNull @FutureOrPresent LocalDate startAt,
        @NotNull @Future LocalDate endAt
) {}
