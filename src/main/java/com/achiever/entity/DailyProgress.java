package com.achiever.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "daily_progress",
       uniqueConstraints = @UniqueConstraint(columnNames = {"challenge_id", "user_id", "date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate date;

    // Legacy field - total distance (kept for backward compatibility)
    @Column(name = "distance_meters", nullable = false)
    @Builder.Default
    private Integer distanceMeters = 0;

    // Per-sport distance tracking
    @Column(name = "run_meters")
    @Builder.Default
    private Integer runMeters = 0;

    @Column(name = "ride_meters")
    @Builder.Default
    private Integer rideMeters = 0;

    @Column(name = "swim_meters")
    @Builder.Default
    private Integer swimMeters = 0;

    @Column(name = "walk_meters")
    @Builder.Default
    private Integer walkMeters = 0;

    @Column(name = "progress_percent", nullable = false)
    @Builder.Default
    private Integer progressPercent = 0; // 0-100, overall normalized average

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    // Helper to get distance by sport type
    public int getDistanceMeters(SportType sportType) {
        return switch (sportType) {
            case RUN -> runMeters != null ? runMeters : 0;
            case RIDE -> rideMeters != null ? rideMeters : 0;
            case SWIM -> swimMeters != null ? swimMeters : 0;
            case WALK -> walkMeters != null ? walkMeters : 0;
        };
    }

    // Helper to set distance by sport type
    public void setDistanceMeters(SportType sportType, int meters) {
        switch (sportType) {
            case RUN -> runMeters = meters;
            case RIDE -> rideMeters = meters;
            case SWIM -> swimMeters = meters;
            case WALK -> walkMeters = meters;
        }
    }

    // Helper to get all distances as Map
    public Map<SportType, Integer> getAllDistances() {
        Map<SportType, Integer> distances = new HashMap<>();
        distances.put(SportType.RUN, runMeters != null ? runMeters : 0);
        distances.put(SportType.RIDE, rideMeters != null ? rideMeters : 0);
        distances.put(SportType.SWIM, swimMeters != null ? swimMeters : 0);
        distances.put(SportType.WALK, walkMeters != null ? walkMeters : 0);
        return distances;
    }
}
