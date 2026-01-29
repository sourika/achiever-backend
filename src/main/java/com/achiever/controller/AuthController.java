package com.achiever.controller;

import com.achiever.dto.*;
import com.achiever.entity.User;
import com.achiever.repository.UserRepository;
import com.achiever.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${spring.security.oauth2.client.registration.strava.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.strava.redirect-uri}")
    private String redirectUri;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Redirect to Strava OAuth
     */
    @GetMapping("/strava")
    public RedirectView stravaAuth(@RequestParam(required = false) String prompt) {
        String scope = "read,activity:read_all";  // Было: read,activity:read

        String url = "https://www.strava.com/oauth/authorize" +
                "?client_id=" + clientId +
                "&response_type=code" +
                "&redirect_uri=" + redirectUri +
                "&scope=" + scope;

        if ("consent".equals(prompt)) {
            url += "&approval_prompt=force";
        }

        return new RedirectView(url);
    }

    /**
     * Handle Strava OAuth callback
     */
    @GetMapping("/strava/callback")
    public RedirectView stravaCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error) {

        if (error != null || code == null) {
            log.warn("Strava auth error: {}", error);
            return new RedirectView(frontendUrl + "/login?error=strava_auth_failed");
        }

        try {
            AuthResponse response = authService.handleStravaCallback(code);
            
            // Redirect to frontend with token
            String redirectUrl = String.format(
                    "%s/auth/callback?token=%s",
                    frontendUrl,
                    response.token()
            );
            
            return new RedirectView(redirectUrl);
        } catch (Exception e) {
            log.error("Strava callback error", e);
            return new RedirectView(frontendUrl + "/login?error=auth_failed");
        }
    }

    /**
     * Get current user
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No token provided"));
            }

            String token = authHeader.substring(7);
            UUID userId = authService.validateTokenAndGetUserId(token);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            return ResponseEntity.ok(UserDTO.fromUser(user));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid token"));
        }
    }

    @GetMapping("/check-email")
    public EmailCheckResponse checkEmail(@RequestParam String email) {
        Optional<User> user = userRepository.findByEmail(email.toLowerCase().trim());
        if (user.isPresent()) {
            boolean hasPassword = user.get().getPasswordHash() != null;
            return new EmailCheckResponse(true, hasPassword);
        }
        return new EmailCheckResponse(false, false);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail().toLowerCase().trim());

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "User not found"));
        }

        User user = userOpt.get();

        if (user.getPasswordHash() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Password not set. Please login with Strava."));
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid password"));
        }

        String token = authService.generateToken(user);
        return ResponseEntity.ok(Map.of("token", token));
    }

    @PostMapping("/set-password")
    public ResponseEntity<?> setPassword(@RequestBody SetPasswordRequest request,
                                         @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            UUID userId = authService.validateTokenAndGetUserId(token);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            userRepository.save(user);

            return ResponseEntity.ok(Map.of("message", "Password set successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        }
    }
}
