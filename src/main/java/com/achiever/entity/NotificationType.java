package com.achiever.entity;

public enum NotificationType {
    CHALLENGE_JOINED,       // Opponent joined your challenge
    CHALLENGE_STARTED,      // Challenge became ACTIVE
    CHALLENGE_COMPLETED,    // Challenge ended naturally
    CHALLENGE_WON,          // You won
    CHALLENGE_LOST,         // You lost
    CHALLENGE_TIE,          // It's a tie
    CHALLENGE_FORFEITED,    // Opponent forfeited, you won
    CHALLENGE_EXPIRED       // No one joined, challenge expired
}