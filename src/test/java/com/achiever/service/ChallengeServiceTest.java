package com.achiever.service;

import com.achiever.dto.CreateChallengeRequest;
import com.achiever.dto.ChallengeDTO;
import com.achiever.dto.JoinChallengeRequest;
import com.achiever.entity.*;
import com.achiever.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChallengeServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeParticipantRepository participantRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ChallengeService challengeService;

    private User testUser;
    private User opponent;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .timezone("America/Los_Angeles")
                .build();

        opponent = User.builder()
                .id(UUID.randomUUID())
                .username("opponent")
                .email("opponent@example.com")
                .timezone("America/Los_Angeles")
                .build();
    }

    @Nested
    @DisplayName("Create Challenge Tests")
    class CreateChallengeTests {

        @Test
        @DisplayName("Should create challenge with valid data")
        void shouldCreateChallengeWithValidData() {
            // Given
            Map<SportType, BigDecimal> goals = new HashMap<>();
            goals.put(SportType.RUN, new BigDecimal("50"));
            
            CreateChallengeRequest request = new CreateChallengeRequest(
                    "Test Challenge",
                    goals,
                    LocalDate.now().plusDays(1),
                    LocalDate.now().plusDays(8),
                    "UTC"
            );

            when(challengeRepository.findByInviteCode(anyString())).thenReturn(Optional.empty());
            when(challengeRepository.save(any(Challenge.class))).thenAnswer(invocation -> {
                Challenge c = invocation.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });
            when(participantRepository.save(any(ChallengeParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ChallengeDTO result = challengeService.createChallenge(testUser, request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("Test Challenge");
            assertThat(result.status()).isEqualTo(ChallengeStatus.PENDING);
            assertThat(result.sportTypes()).contains(SportType.RUN);
            assertThat(result.participants()).hasSize(1);
            
            verify(challengeRepository).save(any(Challenge.class));
            verify(participantRepository).save(any(ChallengeParticipant.class));
        }

        @Test
        @DisplayName("Should reject challenge with past start date")
        void shouldRejectChallengeWithPastStartDate() {
            // Given
            Map<SportType, BigDecimal> goals = new HashMap<>();
            goals.put(SportType.RUN, new BigDecimal("50"));
            
            CreateChallengeRequest request = new CreateChallengeRequest(
                    "Test Challenge",
                    goals,
                    LocalDate.now().minusDays(1), // Past date
                    LocalDate.now().plusDays(7),
                    "UTC"
            );

            // When/Then
            assertThatThrownBy(() -> challengeService.createChallenge(testUser, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Start date cannot be in the past");
        }

        @Test
        @DisplayName("Should reject challenge with end date before start date")
        void shouldRejectChallengeWithInvalidDateRange() {
            // Given
            Map<SportType, BigDecimal> goals = new HashMap<>();
            goals.put(SportType.RUN, new BigDecimal("50"));
            
            CreateChallengeRequest request = new CreateChallengeRequest(
                    "Test Challenge",
                    goals,
                    LocalDate.now().plusDays(7),
                    LocalDate.now().plusDays(1), // Before start
                    "UTC"
            );

            // When/Then
            assertThatThrownBy(() -> challengeService.createChallenge(testUser, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("End date must be after start date");
        }

        @Test
        @DisplayName("Should reject challenge without goals")
        void shouldRejectChallengeWithoutGoals() {
            // Given
            CreateChallengeRequest request = new CreateChallengeRequest(
                    "Test Challenge",
                    new HashMap<>(), // Empty goals
                    LocalDate.now().plusDays(1),
                    LocalDate.now().plusDays(8),
                    "UTC"
            );

            // When/Then
            assertThatThrownBy(() -> challengeService.createChallenge(testUser, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("At least one sport goal must be specified");
        }

        @Test
        @DisplayName("Should create challenge with multiple sports")
        void shouldCreateChallengeWithMultipleSports() {
            // Given
            Map<SportType, BigDecimal> goals = new HashMap<>();
            goals.put(SportType.RUN, new BigDecimal("50"));
            goals.put(SportType.RIDE, new BigDecimal("100"));
            goals.put(SportType.SWIM, new BigDecimal("5"));
            
            CreateChallengeRequest request = new CreateChallengeRequest(
                    "Multi-Sport Challenge",
                    goals,
                    LocalDate.now().plusDays(1),
                    LocalDate.now().plusDays(15),
                    "UTC"
            );

            when(challengeRepository.findByInviteCode(anyString())).thenReturn(Optional.empty());
            when(challengeRepository.save(any(Challenge.class))).thenAnswer(invocation -> {
                Challenge c = invocation.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });
            when(participantRepository.save(any(ChallengeParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ChallengeDTO result = challengeService.createChallenge(testUser, request);

            // Then
            assertThat(result.sportTypes()).containsExactlyInAnyOrder(SportType.RUN, SportType.RIDE, SportType.SWIM);
        }
    }

    @Nested
    @DisplayName("Join Challenge Tests")
    class JoinChallengeTests {

        @Test
        @DisplayName("Should allow user to join challenge with valid invite code")
        void shouldJoinChallengeWithValidCode() {
            // Given
            Challenge challenge = createTestChallenge(ChallengeStatus.PENDING);
            
            Map<SportType, BigDecimal> goals = new HashMap<>();
            goals.put(SportType.RUN, new BigDecimal("60"));
            
            JoinChallengeRequest request = new JoinChallengeRequest(goals);

            when(challengeRepository.findByInviteCodeWithParticipants("TESTCODE")).thenReturn(Optional.of(challenge));
            when(participantRepository.existsByChallengeIdAndUserId(challenge.getId(), opponent.getId())).thenReturn(false);
            when(participantRepository.save(any(ChallengeParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(challengeRepository.save(any(Challenge.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ChallengeDTO result = challengeService.joinChallenge(opponent, "TESTCODE", request);

            // Then
            assertThat(result).isNotNull();
            verify(participantRepository).save(any(ChallengeParticipant.class));
            verify(notificationService).notifyOpponentJoined(any(), any(), any());
        }

        @Test
        @DisplayName("Should reject duplicate join")
        void shouldRejectDuplicateJoin() {
            // Given
            Challenge challenge = createTestChallenge(ChallengeStatus.PENDING);
            
            Map<SportType, BigDecimal> goals = new HashMap<>();
            goals.put(SportType.RUN, new BigDecimal("60"));
            
            JoinChallengeRequest request = new JoinChallengeRequest(goals);

            when(challengeRepository.findByInviteCodeWithParticipants("TESTCODE")).thenReturn(Optional.of(challenge));
            when(participantRepository.existsByChallengeIdAndUserId(challenge.getId(), opponent.getId())).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> challengeService.joinChallenge(opponent, "TESTCODE", request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Already joined this challenge");
        }

        @Test
        @DisplayName("Should reject join when challenge is full")
        void shouldRejectJoinWhenChallengeFull() {
            // Given
            Challenge challenge = createTestChallenge(ChallengeStatus.ACTIVE);
            // Add second participant to make it full
            ChallengeParticipant secondParticipant = ChallengeParticipant.builder()
                    .challenge(challenge)
                    .user(User.builder().id(UUID.randomUUID()).username("third").email("third@test.com").build())
                    .build();
            challenge.getParticipants().add(secondParticipant);
            
            Map<SportType, BigDecimal> goals = new HashMap<>();
            goals.put(SportType.RUN, new BigDecimal("60"));
            
            JoinChallengeRequest request = new JoinChallengeRequest(goals);

            when(challengeRepository.findByInviteCodeWithParticipants("TESTCODE")).thenReturn(Optional.of(challenge));
            when(participantRepository.existsByChallengeIdAndUserId(challenge.getId(), opponent.getId())).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> challengeService.joinChallenge(opponent, "TESTCODE", request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Challenge is full");
        }

        @Test
        @DisplayName("Should reject invalid invite code")
        void shouldRejectInvalidInviteCode() {
            // Given
            when(challengeRepository.findByInviteCodeWithParticipants("INVALID")).thenReturn(Optional.empty());
            
            Map<SportType, BigDecimal> goals = new HashMap<>();
            goals.put(SportType.RUN, new BigDecimal("60"));
            
            JoinChallengeRequest request = new JoinChallengeRequest(goals);

            // When/Then
            assertThatThrownBy(() -> challengeService.joinChallenge(opponent, "INVALID", request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid invite code");
        }
    }

    @Nested
    @DisplayName("Delete Challenge Tests")
    class DeleteChallengeTests {

        @Test
        @DisplayName("Should allow creator to delete PENDING challenge")
        void shouldAllowCreatorToDeletePendingChallenge() {
            // Given
            Challenge challenge = createTestChallenge(ChallengeStatus.PENDING);
            
            when(challengeRepository.findByIdWithParticipants(challenge.getId())).thenReturn(Optional.of(challenge));

            // When
            challengeService.deleteChallenge(challenge.getId(), testUser);

            // Then
            verify(challengeRepository).delete(challenge);
        }

        @Test
        @DisplayName("Should not allow non-creator to delete challenge")
        void shouldNotAllowNonCreatorToDelete() {
            // Given
            Challenge challenge = createTestChallenge(ChallengeStatus.PENDING);
            
            when(challengeRepository.findByIdWithParticipants(challenge.getId())).thenReturn(Optional.of(challenge));

            // When/Then
            assertThatThrownBy(() -> challengeService.deleteChallenge(challenge.getId(), opponent))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Only the creator can delete");
        }

        @Test
        @DisplayName("Should not allow deletion of ACTIVE challenge")
        void shouldNotAllowDeletionOfActiveChallenge() {
            // Given
            Challenge challenge = createTestChallenge(ChallengeStatus.ACTIVE);
            
            when(challengeRepository.findByIdWithParticipants(challenge.getId())).thenReturn(Optional.of(challenge));

            // When/Then
            assertThatThrownBy(() -> challengeService.deleteChallenge(challenge.getId(), testUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot delete active challenge");
        }
    }

    @Nested
    @DisplayName("Determine Winner Tests")
    class DetermineWinnerTests {

        @Test
        @DisplayName("Should return winner when one participant forfeits")
        void shouldReturnWinnerWhenOpponentForfeits() {
            // Given
            Challenge challenge = createTestChallenge(ChallengeStatus.ACTIVE);
            
            ChallengeParticipant opponentParticipant = ChallengeParticipant.builder()
                    .challenge(challenge)
                    .user(opponent)
                    .forfeitedAt(java.time.LocalDateTime.now())
                    .build();
            challenge.getParticipants().add(opponentParticipant);

            // When
            User winner = challengeService.determineWinner(challenge);

            // Then
            assertThat(winner).isEqualTo(testUser);
        }

        @Test
        @DisplayName("Should return null when no active participants")
        void shouldReturnNullWhenNoActiveParticipants() {
            // Given
            Challenge challenge = createTestChallenge(ChallengeStatus.ACTIVE);
            // Forfeit the only participant
            challenge.getParticipants().getFirst().setForfeitedAt(java.time.LocalDateTime.now());

            // When
            User winner = challengeService.determineWinner(challenge);

            // Then
            assertThat(winner).isNull();
        }
    }

    // Helper method
    private Challenge createTestChallenge(ChallengeStatus status) {
        Challenge challenge = Challenge.builder()
                .id(UUID.randomUUID())
                .createdBy(testUser)
                .inviteCode("TESTCODE")
                .startAt(LocalDate.now())
                .endAt(LocalDate.now().plusDays(7))
                .status(status)
                .build();
        challenge.setSportTypeSet(Set.of(SportType.RUN));
        
        ChallengeParticipant participant = ChallengeParticipant.builder()
                .challenge(challenge)
                .user(testUser)
                .build();
        Map<SportType, BigDecimal> goals = new HashMap<>();
        goals.put(SportType.RUN, new BigDecimal("50"));
        participant.setGoals(goals);
        
        challenge.setParticipants(new ArrayList<>(List.of(participant)));
        
        return challenge;
    }
}
