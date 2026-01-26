package com.achiever.controller;

import com.achiever.dto.AuthResponse;
import com.achiever.dto.UserDTO;
import com.achiever.entity.User;
import com.achiever.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

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
        String scope = "read,activity:read";

        String url = "https://www.strava.com/oauth/authorize" +
                "?client_id=" + clientId +
                "&response_type=code" +
                "&redirect_uri=" + redirectUri +
                "&scope=" + scope;

        // Если prompt=consent, добавляем approval_prompt=force
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
    public ResponseEntity<UserDTO> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.getCurrentUser(user));
    }
}
