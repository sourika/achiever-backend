package com.achiever.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "challenge_participants",
       uniqueConstraints = @UniqueConstraint(columnNames = {"challenge_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChallengeParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Legacy field - kept for backward compatibility
    @Column(name = "goal_value", precision = 10, scale = 2)
    private BigDecimal goalValue;

    // Individual sport goals in kilometers
    @Column(name = "goal_run_km", precision = 10, scale = 2)
    private BigDecimal goalRunKm;

    @Column(name = "goal_ride_km", precision = 10, scale = 2)
    private BigDecimal goalRideKm;

    @Column(name = "goal_swim_km", precision = 10, scale = 2)
    private BigDecimal goalSwimKm;

    @Column(name = "goal_walk_km", precision = 10, scale = 2)
    private BigDecimal goalWalkKm;

    @Column(name = "joined_at", nullable = false)
    @Builder.Default
    private Instant joinedAt = Instant.now();

    @Column(name = "forfeited_at")
    private LocalDateTime forfeitedAt;


    public boolean hasForfeited() {
        return forfeitedAt != null;
    }

    // Helper to get goals as Map
    public Map<SportType, BigDecimal> getGoals() {
        Map<SportType, BigDecimal> goals = new HashMap<>();
        if (goalRunKm != null) goals.put(SportType.RUN, goalRunKm);
        if (goalRideKm != null) goals.put(SportType.RIDE, goalRideKm);
        if (goalSwimKm != null) goals.put(SportType.SWIM, goalSwimKm);
        if (goalWalkKm != null) goals.put(SportType.WALK, goalWalkKm);
        return goals;
    }

    // Helper to set goals from Map
    public void setGoals(Map<SportType, BigDecimal> goals) {
        this.goalRunKm = goals.get(SportType.RUN);
        this.goalRideKm = goals.get(SportType.RIDE);
        this.goalSwimKm = goals.get(SportType.SWIM);
        this.goalWalkKm = goals.get(SportType.WALK);
    }

    // Helper to get goal in meters for a specific sport
    public int getGoalMeters(SportType sportType) {
        BigDecimal km = switch (sportType) {
            case RUN -> goalRunKm;
            case RIDE -> goalRideKm;
            case SWIM -> goalSwimKm;
            case WALK -> goalWalkKm;
        };
        if (km == null) return 0;
        return km.multiply(BigDecimal.valueOf(1000)).intValue();
    }

    // Legacy helper - kept for backward compatibility
    public int getGoalMeters() {
        if (goalValue != null) {
            return goalValue.multiply(BigDecimal.valueOf(1000)).intValue();
        }
        return 0;
    }
}
