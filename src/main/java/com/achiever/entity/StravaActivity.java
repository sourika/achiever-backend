package com.achiever.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.OffsetDateTime;

@Entity
@Table(name = "strava_activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StravaActivity {

    @Id
    private Long id; // Strava's activity ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "sport_type", nullable = false, length = 20)
    private String sportType; // Strava's sport type string

    @Column(length = 255)
    private String name;

    @Column(name = "start_date", nullable = false)
    private OffsetDateTime startDate;

    @Column(name = "distance_meters", nullable = false)
    private Integer distanceMeters;

    @Column(name = "moving_time_seconds")
    private Integer movingTimeSeconds;

    @Column(name = "synced_at", nullable = false)
    @Builder.Default
    private Instant syncedAt = Instant.now();
}
