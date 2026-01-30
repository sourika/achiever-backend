package com.achiever.entity;

public enum ChallengeStatus {
    PENDING,    // Created, waiting for opponent
    SCHEDULED,  // Opponent joined, waiting for startAt
    ACTIVE,     // In progress
    COMPLETED,  // Finished (has winner or tie)
    EXPIRED,    // endAt passed without opponent joining
    CANCELLED   // Cancelled by creator (future use)
}
