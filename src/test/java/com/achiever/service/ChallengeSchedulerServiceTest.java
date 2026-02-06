package com.achiever.service;

import com.achiever.entity.*;
import com.achiever.repository.*;
import com.achiever.strava.StravaSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChallengeSchedulerServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeService challengeService;

    @Mock
    private StravaSyncService stravaSyncService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ChallengeSchedulerService schedulerService;

    private User creator;
    private User opponent;

    @BeforeEach
    void setUp() {
        creator = User.builder()
                .id(UUID.randomUUID())
                .username("creator")
                .email("creator@test.com")
                .build();

        opponent = User.builder()
                .id(UUID.randomUUID())
                .username("opponent")
                .email("opponent@test.com")
                .build();
    }

    @Test
    @DisplayName("Should transition PENDING to EXPIRED when end date passed")
    void shouldTransitionPendingToExpired() {
        // Given
        Challenge pendingChallenge = createChallenge(ChallengeStatus.PENDING, LocalDate.now().minusDays(3), LocalDate.now().minusDays(1));
        
        when(challengeRepository.findByStatus(ChallengeStatus.PENDING))
                .thenReturn(List.of(pendingChallenge));
        when(challengeRepository.findByStatus(ChallengeStatus.SCHEDULED))
                .thenReturn(Collections.emptyList());

        // When
        schedulerService.processStatusTransitions(LocalDate.now());

        // Then
        verify(challengeRepository).save(argThat(challenge -> 
                challenge.getStatus() == ChallengeStatus.EXPIRED
        ));
        verify(notificationService).notifyChallengeExpired(pendingChallenge);
    }

    @Test
    @DisplayName("Should transition SCHEDULED to ACTIVE when start date reached")
    void shouldTransitionScheduledToActive() {
        // Given
        Challenge scheduledChallenge = createChallenge(ChallengeStatus.SCHEDULED, LocalDate.now().minusDays(1), LocalDate.now().plusDays(7));
        
        when(challengeRepository.findByStatus(ChallengeStatus.PENDING))
                .thenReturn(Collections.emptyList());
        when(challengeRepository.findByStatus(ChallengeStatus.SCHEDULED))
                .thenReturn(List.of(scheduledChallenge));

        // When
        schedulerService.processStatusTransitions(LocalDate.now());

        // Then
        verify(challengeRepository).save(argThat(challenge -> 
                challenge.getStatus() == ChallengeStatus.ACTIVE
        ));
        verify(notificationService).notifyChallengeStarted(scheduledChallenge);
    }

    @Test
    @DisplayName("Should not transition SCHEDULED if start date is in future")
    void shouldNotTransitionScheduledIfStartInFuture() {
        // Given
        Challenge scheduledChallenge = createChallenge(ChallengeStatus.SCHEDULED, LocalDate.now().plusDays(3), LocalDate.now().plusDays(10));
        
        when(challengeRepository.findByStatus(ChallengeStatus.PENDING))
                .thenReturn(Collections.emptyList());
        when(challengeRepository.findByStatus(ChallengeStatus.SCHEDULED))
                .thenReturn(List.of(scheduledChallenge));

        // When
        schedulerService.processStatusTransitions(LocalDate.now());

        // Then
        verify(challengeRepository, never()).save(any());
        verify(notificationService, never()).notifyChallengeStarted(any());
    }

    @Test
    @DisplayName("Should complete expired ACTIVE challenges")
    void shouldCompleteExpiredActiveChallenges() {
        // Given
        Challenge activeChallenge = createChallengeWithParticipants(ChallengeStatus.ACTIVE, LocalDate.now().minusDays(10), LocalDate.now().minusDays(1));
        
        when(challengeRepository.findByStatus(ChallengeStatus.ACTIVE))
                .thenReturn(List.of(activeChallenge));
        when(challengeService.determineWinner(activeChallenge)).thenReturn(creator);

        // When
        schedulerService.completeExpiredChallenges(LocalDate.now());

        // Then
        verify(challengeRepository).save(argThat(challenge -> 
                challenge.getStatus() == ChallengeStatus.COMPLETED &&
                challenge.getWinner() == creator
        ));
        verify(notificationService).notifyChallengeCompleted(activeChallenge, creator);
    }

    @Test
    @DisplayName("Should sync all active challenge participants")
    void shouldSyncAllActiveChallengeParticipants() {
        // Given
        StravaConnection stravaConnection = StravaConnection.builder()
                .athleteId(12345L)
                .accessToken("token")
                .build();
        creator.setStravaConnection(stravaConnection);
        
        Challenge activeChallenge = createChallengeWithParticipants(ChallengeStatus.ACTIVE, LocalDate.now().minusDays(3), LocalDate.now().plusDays(4));
        
        when(challengeRepository.findByStatus(ChallengeStatus.ACTIVE))
                .thenReturn(List.of(activeChallenge));

        // When
        schedulerService.syncAllActiveChallenges();

        // Then
        verify(stravaSyncService).syncAndUpdateProgress(eq(creator), eq(activeChallenge));
    }

    @Test
    @DisplayName("Should skip forfeited participants during sync")
    void shouldSkipForfeitedParticipantsDuringSync() {
        // Given
        StravaConnection stravaConnection = StravaConnection.builder()
                .athleteId(12345L)
                .accessToken("token")
                .build();
        creator.setStravaConnection(stravaConnection);
        opponent.setStravaConnection(stravaConnection);
        
        Challenge activeChallenge = createChallengeWithParticipants(ChallengeStatus.ACTIVE, LocalDate.now().minusDays(3), LocalDate.now().plusDays(4));
        // Forfeit opponent
        activeChallenge.getParticipants().get(1).setForfeitedAt(java.time.LocalDateTime.now());
        
        when(challengeRepository.findByStatus(ChallengeStatus.ACTIVE))
                .thenReturn(List.of(activeChallenge));

        // When
        schedulerService.syncAllActiveChallenges();

        // Then
        verify(stravaSyncService).syncAndUpdateProgress(eq(creator), eq(activeChallenge));
        verify(stravaSyncService, never()).syncAndUpdateProgress(eq(opponent), any());
    }

    // Helper methods
    private Challenge createChallenge(ChallengeStatus status, LocalDate startAt, LocalDate endAt) {
        Challenge challenge = Challenge.builder()
                .id(UUID.randomUUID())
                .createdBy(creator)
                .inviteCode("TESTCODE")
                .startAt(startAt)
                .endAt(endAt)
                .status(status)
                .build();
        challenge.setSportTypeSet(Set.of(SportType.RUN));
        challenge.setParticipants(new ArrayList<>());
        return challenge;
    }

    private Challenge createChallengeWithParticipants(ChallengeStatus status, LocalDate startAt, LocalDate endAt) {
        Challenge challenge = createChallenge(status, startAt, endAt);
        
        ChallengeParticipant creatorParticipant = ChallengeParticipant.builder()
                .challenge(challenge)
                .user(creator)
                .build();
        Map<SportType, BigDecimal> goals = new HashMap<>();
        goals.put(SportType.RUN, new BigDecimal("50"));
        creatorParticipant.setGoals(goals);
        
        ChallengeParticipant opponentParticipant = ChallengeParticipant.builder()
                .challenge(challenge)
                .user(opponent)
                .build();
        opponentParticipant.setGoals(goals);
        
        challenge.setParticipants(new ArrayList<>(List.of(creatorParticipant, opponentParticipant)));
        
        return challenge;
    }
}
