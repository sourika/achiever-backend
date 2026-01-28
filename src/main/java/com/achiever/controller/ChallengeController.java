package com.achiever.controller;

import com.achiever.dto.*;
import com.achiever.entity.User;
import com.achiever.service.ChallengeService;
import com.achiever.strava.StravaSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/challenges")
@RequiredArgsConstructor
@Slf4j
public class ChallengeController {

    private final ChallengeService challengeService;
    private final StravaSyncService stravaSyncService;

    /**
     * Create a new challenge
     */
    @PostMapping
    public ResponseEntity<ChallengeDTO> createChallenge(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateChallengeRequest request) {

        ChallengeDTO challenge = challengeService.createChallenge(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(challenge);
    }

    /**
     * Get challenge by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ChallengeDTO> getChallenge(@PathVariable UUID id) {
        return ResponseEntity.ok(challengeService.getChallenge(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ChallengeDTO> updateChallenge(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateChallengeRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(challengeService.updateChallenge(id, request, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChallenge(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        challengeService.deleteChallenge(id, user);
        return ResponseEntity.noContent().build();
    }

    /**
     * Leave (forfeit) a challenge - for non-creator participants
     */
    @PostMapping("/{id}/leave")
    public ResponseEntity<ChallengeDTO> leaveChallenge(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(challengeService.leaveChallenge(id, user));
    }

    /**
     * Finish challenge early - for creator when opponent left
     */
    @PostMapping("/{id}/finish")
    public ResponseEntity<ChallengeDTO> finishChallenge(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(challengeService.finishChallenge(id, user));
    }

    /**
     * Get challenge preview by invite code (public)
     */
    @GetMapping("/invite/{code}")
    public ResponseEntity<ChallengeDTO> getChallengeByInviteCode(@PathVariable String code) {
        return ResponseEntity.ok(challengeService.getChallengeByInviteCode(code));
    }

    /**
     * Join challenge by invite code
     */
    @PostMapping("/invite/{code}/join")
    public ResponseEntity<ChallengeDTO> joinChallenge(
            @AuthenticationPrincipal User user,
            @PathVariable String code,
            @Valid @RequestBody JoinChallengeRequest request) {

        ChallengeDTO challenge = challengeService.joinChallenge(user, code, request);
        return ResponseEntity.ok(challenge);
    }

    /**
     * Get challenge progress
     */
    @GetMapping("/{id}/progress")
    public ResponseEntity<ChallengeProgressDTO> getChallengeProgress(@PathVariable UUID id) {
        return ResponseEntity.ok(challengeService.getChallengeProgress(id));
    }

    /**
     * Get current user's challenges
     */
    @GetMapping("/my")
    public ResponseEntity<List<ChallengeDTO>> getMyChallenges(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(challengeService.getUserChallenges(user.getId()));
    }

    /**
     * Get current user's active challenges
     */
    @GetMapping("/my/active")
    public ResponseEntity<List<ChallengeDTO>> getMyActiveChallenges(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(challengeService.getUserActiveChallenges(user.getId()));
    }

    /**
     * Manually trigger Strava sync for current user
     */
    @PostMapping("/{id}/sync")
    public ResponseEntity<ChallengeProgressDTO> syncAndGetProgress(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {

        // Sync user's activities
        stravaSyncService.syncUserActivities(user.getId());
        
        // Return updated progress
        return ResponseEntity.ok(challengeService.getChallengeProgress(id));
    }
}
