package com.achiever.service;

import com.achiever.dto.NotificationDTO;
import com.achiever.entity.*;
import com.achiever.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Create a notification for a user
     */
    public void notify(User user, NotificationType type, Challenge challenge, String message) {
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .challenge(challenge)
                .message(message)
                .build();
        notificationRepository.save(notification);
        log.info("Notification created for user {}: {} - {}", user.getUsername(), type, message);
    }

    /**
     * Get all notifications for a user
     */
    @Transactional(readOnly = true)
    public List<NotificationDTO> getUserNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToDTO)
                .toList();
    }

    /**
     * Get unread count
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    /**
     * Mark all notifications as read
     */
    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsRead(userId);
        log.info("All notifications marked as read for user {}", userId);
    }

    // ============================================================
    // Convenience methods for common notification types
    // ============================================================

    /**
     * Notify challenge creator that opponent joined
     */
    public void notifyOpponentJoined(User creator, User opponent, Challenge challenge) {
        String challengeName = challenge.getName() != null ? challenge.getName() : "Challenge";
        notify(creator, NotificationType.CHALLENGE_JOINED, challenge,
                opponent.getUsername() + " joined \"" + challengeName + "\"");
    }

    /**
     * Notify both participants that challenge started
     */
    public void notifyChallengeStarted(Challenge challenge) {
        String challengeName = challenge.getName() != null ? challenge.getName() : "Challenge";
        for (ChallengeParticipant p : challenge.getParticipants()) {
            notify(p.getUser(), NotificationType.CHALLENGE_STARTED, challenge,
                    "\"" + challengeName + "\" has started!");
        }
    }

    /**
     * Notify participants about challenge completion
     */
    public void notifyChallengeCompleted(Challenge challenge, User winner) {
        String challengeName = challenge.getName() != null ? challenge.getName() : "Challenge";

        for (ChallengeParticipant p : challenge.getParticipants()) {
            if (p.hasForfeited()) continue;

            if (winner == null) {
                // Tie
                notify(p.getUser(), NotificationType.CHALLENGE_TIE, challenge,
                        "\"" + challengeName + "\" ended in a tie! 🤝");
            } else if (p.getUser().getId().equals(winner.getId())) {
                // Winner
                notify(p.getUser(), NotificationType.CHALLENGE_WON, challenge,
                        "You won \"" + challengeName + "\"! 🏆");
            } else {
                // Loser
                notify(p.getUser(), NotificationType.CHALLENGE_LOST, challenge,
                        "\"" + challengeName + "\" has ended. Better luck next time!");
            }
        }
    }

    /**
     * Notify opponent that they won because user forfeited
     */
    public void notifyOpponentForfeited(User forfeiter, User winner, Challenge challenge) {
        String challengeName = challenge.getName() != null ? challenge.getName() : "Challenge";
        notify(winner, NotificationType.CHALLENGE_FORFEITED, challenge,
                forfeiter.getUsername() + " forfeited \"" + challengeName + "\". You won! 🏆");
    }

    /**
     * Notify creator that challenge expired
     */
    public void notifyChallengeExpired(Challenge challenge) {
        String challengeName = challenge.getName() != null ? challenge.getName() : "Challenge";
        notify(challenge.getCreatedBy(), NotificationType.CHALLENGE_EXPIRED, challenge,
                "\"" + challengeName + "\" expired — no one joined");
    }

    private NotificationDTO mapToDTO(Notification n) {
        return new NotificationDTO(
                n.getId(),
                n.getType(),
                n.getChallenge() != null ? n.getChallenge().getId() : null,
                n.getChallenge() != null ? n.getChallenge().getName() : null,
                n.getMessage(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}