package com.achiever.service;

import com.achiever.entity.*;
import com.achiever.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User creator;
    private User opponent;
    private Challenge challenge;

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

        challenge = Challenge.builder()
                .id(UUID.randomUUID())
                .createdBy(creator)
                .inviteCode("TESTCODE")
                .name("Test Challenge")
                .startAt(LocalDate.now())
                .endAt(LocalDate.now().plusDays(7))
                .status(ChallengeStatus.ACTIVE)
                .build();
        challenge.setSportTypeSet(Set.of(SportType.RUN));
        
        ChallengeParticipant creatorParticipant = ChallengeParticipant.builder()
                .challenge(challenge)
                .user(creator)
                .build();
        ChallengeParticipant opponentParticipant = ChallengeParticipant.builder()
                .challenge(challenge)
                .user(opponent)
                .build();
        challenge.setParticipants(new ArrayList<>(List.of(creatorParticipant, opponentParticipant)));
    }

    @Test
    @DisplayName("Should notify creator when opponent joins")
    void shouldNotifyCreatorWhenOpponentJoins() {
        // Given
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        notificationService.notifyOpponentJoined(creator, opponent, challenge);

        // Then
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(creator);
        assertThat(saved.getType()).isEqualTo(NotificationType.CHALLENGE_JOINED);
        assertThat(saved.getChallenge()).isEqualTo(challenge);
        assertThat(saved.getMessage()).contains("opponent");
    }

    @Test
    @DisplayName("Should notify both participants when challenge starts")
    void shouldNotifyBothParticipantsWhenChallengeStarts() {
        // Given
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        notificationService.notifyChallengeStarted(challenge);

        // Then
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    @DisplayName("Should notify both participants when challenge completes with winner")
    void shouldNotifyBothParticipantsWhenChallengeCompletesWithWinner() {
        // Given
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        notificationService.notifyChallengeCompleted(challenge, creator);

        // Then
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        List<Notification> notifications = captor.getAllValues();
        
        // One should be CHALLENGE_WON for winner
        boolean hasWonNotification = notifications.stream()
                .anyMatch(n -> n.getType() == NotificationType.CHALLENGE_WON && n.getUser().equals(creator));
        assertThat(hasWonNotification).isTrue();
        
        // One should be CHALLENGE_LOST for loser
        boolean hasLostNotification = notifications.stream()
                .anyMatch(n -> n.getType() == NotificationType.CHALLENGE_LOST && n.getUser().equals(opponent));
        assertThat(hasLostNotification).isTrue();
    }

    @Test
    @DisplayName("Should notify both participants when challenge ends in tie")
    void shouldNotifyBothParticipantsWhenChallengeEndsInTie() {
        // Given
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        notificationService.notifyChallengeCompleted(challenge, null); // null = tie

        // Then
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        List<Notification> notifications = captor.getAllValues();
        
        // Both should be CHALLENGE_TIED
        assertThat(notifications).allMatch(n -> n.getType() == NotificationType.CHALLENGE_TIE);
    }

    @Test
    @DisplayName("Should notify creator when challenge expires")
    void shouldNotifyCreatorWhenChallengeExpires() {
        // Given
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        notificationService.notifyChallengeExpired(challenge);

        // Then
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(creator);
        assertThat(saved.getType()).isEqualTo(NotificationType.CHALLENGE_EXPIRED);
    }
}
