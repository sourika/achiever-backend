package com.achiever.dto;

import com.achiever.entity.ChallengeStatus;
import java.util.List;
import java.util.UUID;

public record ChallengeResultsDTO(
        UUID challengeId,
        ChallengeStatus status,
        List<WeekResultDTO> weeklyResults,
        UUID overallWinnerId,
        int userAWins,
        int userBWins,
        int ties
) {}
