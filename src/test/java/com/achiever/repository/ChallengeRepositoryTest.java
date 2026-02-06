package com.achiever.repository;

import com.achiever.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ChallengeRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ChallengeRepository challengeRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("testuser")
                .email("test@example.com")
                .timezone("UTC")
                .build();
        entityManager.persist(testUser);
        entityManager.flush();
    }

    @Test
    @DisplayName("Should find challenge by invite code")
    void shouldFindByInviteCode() {
        // Given
        Challenge challenge = Challenge.builder()
                .createdBy(testUser)
                .inviteCode("ABCD1234")
                .startAt(LocalDate.now())
                .endAt(LocalDate.now().plusDays(7))
                .status(ChallengeStatus.PENDING)
                .build();
        challenge.setSportTypeSet(Set.of(SportType.RUN));
        entityManager.persist(challenge);
        entityManager.flush();

        // When
        Optional<Challenge> found = challengeRepository.findByInviteCode("ABCD1234");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getInviteCode()).isEqualTo("ABCD1234");
    }

    @Test
    @DisplayName("Should not find challenge by non-existent invite code")
    void shouldNotFindByNonExistentInviteCode() {
        // When
        Optional<Challenge> found = challengeRepository.findByInviteCode("NONEXIST");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should find challenges by status")
    void shouldFindByStatus() {
        // Given
        Challenge pendingChallenge = createChallenge("CODE1", ChallengeStatus.PENDING);
        Challenge activeChallenge = createChallenge("CODE2", ChallengeStatus.ACTIVE);
        Challenge completedChallenge = createChallenge("CODE3", ChallengeStatus.COMPLETED);
        
        entityManager.persist(pendingChallenge);
        entityManager.persist(activeChallenge);
        entityManager.persist(completedChallenge);
        entityManager.flush();

        // When
        List<Challenge> pending = challengeRepository.findByStatus(ChallengeStatus.PENDING);
        List<Challenge> active = challengeRepository.findByStatus(ChallengeStatus.ACTIVE);

        // Then
        assertThat(pending).hasSize(1);
        assertThat(pending.getFirst().getInviteCode()).isEqualTo("CODE1");
        
        assertThat(active).hasSize(1);
        assertThat(active.getFirst().getInviteCode()).isEqualTo("CODE2");
    }

    @Test
    @DisplayName("Should find challenge with participants by invite code")
    void shouldFindByInviteCodeWithParticipants() {
        // Given
        Challenge challenge = Challenge.builder()
                .createdBy(testUser)
                .inviteCode("WITHPART")
                .startAt(LocalDate.now())
                .endAt(LocalDate.now().plusDays(7))
                .status(ChallengeStatus.PENDING)
                .build();
        challenge.setSportTypeSet(Set.of(SportType.RUN));
        
        ChallengeParticipant participant = ChallengeParticipant.builder()
                .challenge(challenge)
                .user(testUser)
                .build();
        participant.setGoals(Map.of(SportType.RUN, new BigDecimal("50")));
        
        challenge.getParticipants().add(participant);
        entityManager.persist(challenge);
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<Challenge> found = challengeRepository.findByInviteCodeWithParticipants("WITHPART");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getParticipants()).hasSize(1);
        assertThat(found.get().getParticipants().getFirst().getUser().getUsername()).isEqualTo("testuser");
    }

    private Challenge createChallenge(String inviteCode, ChallengeStatus status) {
        Challenge challenge = Challenge.builder()
                .createdBy(testUser)
                .inviteCode(inviteCode)
                .startAt(LocalDate.now())
                .endAt(LocalDate.now().plusDays(7))
                .status(status)
                .build();
        challenge.setSportTypeSet(Set.of(SportType.RUN));
        return challenge;
    }
}
