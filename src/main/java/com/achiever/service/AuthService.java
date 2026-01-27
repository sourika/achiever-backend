package com.achiever.service;

import com.achiever.config.JwtUtils;
import com.achiever.dto.AuthResponse;
import com.achiever.dto.UserDTO;
import com.achiever.entity.StravaConnection;
import com.achiever.entity.User;
import com.achiever.repository.StravaConnectionRepository;
import com.achiever.repository.UserRepository;
import com.achiever.strava.StravaApiClient;
import com.achiever.strava.StravaAthleteResponse;
import com.achiever.strava.StravaTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final StravaApiClient stravaApiClient;
    private final UserRepository userRepository;
    private final StravaConnectionRepository stravaConnectionRepository;
    private final JwtUtils jwtUtils;

    @Transactional
    public AuthResponse handleStravaCallback(String code) {
        StravaTokenResponse tokenResponse = stravaApiClient.exchangeCode(code);
        StravaAthleteResponse athlete = tokenResponse.getAthlete();

        log.info("Strava auth for athlete: {} ({})", athlete.getId(), athlete.getEmail());

        Optional<StravaConnection> existingConnection =
                stravaConnectionRepository.findByAthleteId(athlete.getId());

        User user;
        if (existingConnection.isPresent()) {
            StravaConnection connection = existingConnection.get();
            updateStravaTokens(connection, tokenResponse);
            user = connection.getUser();
            log.info("Existing user logged in: {}", user.getId());
        } else {
            user = createNewUser(athlete, tokenResponse);
            log.info("New user created: {}", user.getId());
        }

        String token = jwtUtils.generateToken(user.getId(), user.getEmail());

        return new AuthResponse(token, mapToUserDTO(user));
    }

    /**
     * Generate token for email/password login
     */
    public String generateToken(User user) {
        return jwtUtils.generateToken(user.getId(), user.getEmail());
    }

    /**
     * Validate token and extract user ID
     */
    public UUID validateTokenAndGetUserId(String token) {
        return jwtUtils.getUserIdFromToken(token);
    }

    private User createNewUser(StravaAthleteResponse athlete, StravaTokenResponse tokens) {
        String username = generateUsername(athlete);

        User user = User.builder()
                .username(username)
                .email(athlete.getEmail() != null ? athlete.getEmail() : username + "@strava.local")
                .timezone("America/Los_Angeles")
                .build();

        user = userRepository.save(user);

        StravaConnection connection = StravaConnection.builder()
                .user(user)
                .athleteId(athlete.getId())
                .accessToken(tokens.getAccessToken())
                .refreshToken(tokens.getRefreshToken())
                .expiresAt(Instant.ofEpochSecond(tokens.getExpiresAt()))
                .build();

        stravaConnectionRepository.save(connection);
        user.setStravaConnection(connection);

        return user;
    }

    private void updateStravaTokens(StravaConnection connection, StravaTokenResponse tokens) {
        connection.setAccessToken(tokens.getAccessToken());
        connection.setRefreshToken(tokens.getRefreshToken());
        connection.setExpiresAt(Instant.ofEpochSecond(tokens.getExpiresAt()));
        connection.setUpdatedAt(Instant.now());
        stravaConnectionRepository.save(connection);
    }

    private String generateUsername(StravaAthleteResponse athlete) {
        String base = athlete.getFirstname() != null
                ? athlete.getFirstname().toLowerCase()
                : "user";

        String username = base;
        int counter = 1;
        while (userRepository.existsByUsername(username)) {
            username = base + counter++;
        }
        return username;
    }

    public UserDTO getCurrentUser(User user) {
        return mapToUserDTO(user);
    }

    private UserDTO mapToUserDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getTimezone(),
                user.getStravaConnection() != null,
                user.getPasswordHash() != null
        );
    }
}