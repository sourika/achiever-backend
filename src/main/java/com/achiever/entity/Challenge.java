package com.achiever.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "challenges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "invite_code", unique = true, nullable = false, length = 12)
    private String inviteCode;

    // Stores comma-separated sport types like "RUN,SWIM,RIDE"
    @Column(name = "sport_types", nullable = false, length = 50)
    private String sportTypes;

    @Column(name = "start_at", nullable = false)
    private LocalDate startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDate endAt;

    @Column(name = "name", length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ChallengeStatus status = ChallengeStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "challenge", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChallengeParticipant> participants = new ArrayList<>();

    public void addParticipant(ChallengeParticipant participant) {
        participants.add(participant);
        participant.setChallenge(this);
    }

    // Helper to get sport types as Set
    public Set<SportType> getSportTypeSet() {
        Set<SportType> types = new HashSet<>();
        if (sportTypes != null && !sportTypes.isEmpty()) {
            for (String type : sportTypes.split(",")) {
                types.add(SportType.valueOf(type.trim()));
            }
        }
        return types;
    }

    // Helper to set sport types from Set
    public void setSportTypeSet(Set<SportType> types) {
        if (types == null || types.isEmpty()) {
            this.sportTypes = "";
        } else {
            this.sportTypes = types.stream()
                    .map(Enum::name)
                    .sorted()
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
        }
    }
}
