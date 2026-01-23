package com.achiever.strava;

import com.achiever.entity.StravaConnection;
import com.achiever.repository.StravaConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StravaApiClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final StravaConnectionRepository stravaConnectionRepository;

    @Value("${spring.security.oauth2.client.registration.strava.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.strava.client-secret}")
    private String clientSecret;

    @Value("${app.strava.api-base-url}")
    private String apiBaseUrl;

    /**
     * Exchange authorization code for tokens
     */
    public StravaTokenResponse exchangeCode(String code) {
        String url = "https://www.strava.com/oauth/token";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("code", code);
        params.add("grant_type", "authorization_code");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<StravaTokenResponse> response = restTemplate.postForEntity(
                url, request, StravaTokenResponse.class);

        return response.getBody();
    }

    /**
     * Refresh expired access token
     */
    public StravaTokenResponse refreshToken(String refreshToken) {
        String url = "https://www.strava.com/oauth/token";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("refresh_token", refreshToken);
        params.add("grant_type", "refresh_token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<StravaTokenResponse> response = restTemplate.postForEntity(
                url, request, StravaTokenResponse.class);

        return response.getBody();
    }

    /**
     * Get valid access token, refreshing if needed
     */
    public String getValidAccessToken(StravaConnection connection) {
        if (connection.isExpired()) {
            log.info("Token expired for athlete {}, refreshing...", connection.getAthleteId());
            StravaTokenResponse newTokens = refreshToken(connection.getRefreshToken());

            connection.setAccessToken(newTokens.getAccessToken());
            connection.setRefreshToken(newTokens.getRefreshToken());
            connection.setExpiresAt(Instant.ofEpochSecond(newTokens.getExpiresAt()));
            connection.setUpdatedAt(Instant.now());
            stravaConnectionRepository.save(connection);
        }
        return connection.getAccessToken();
    }

    /**
     * Fetch athlete activities from Strava
     */
    public List<StravaActivityResponse> getActivities(
            StravaConnection connection,
            OffsetDateTime after,
            OffsetDateTime before,
            int page,
            int perPage) {

        String accessToken = getValidAccessToken(connection);

        String url = String.format(
                "%s/athlete/activities?after=%d&before=%d&page=%d&per_page=%d",
                apiBaseUrl,
                after.toEpochSecond(),
                before.toEpochSecond(),
                page,
                perPage);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<StravaActivityResponse[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, StravaActivityResponse[].class);

            StravaActivityResponse[] activities = response.getBody();
            return activities != null ? Arrays.asList(activities) : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch activities from Strava", e);
            return Collections.emptyList();
        }
    }

    /**
     * Fetch current athlete profile
     */
    public StravaAthleteResponse getAthlete(StravaConnection connection) {
        String accessToken = getValidAccessToken(connection);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<StravaAthleteResponse> response = restTemplate.exchange(
                apiBaseUrl + "/athlete", HttpMethod.GET, request, StravaAthleteResponse.class);

        return response.getBody();
    }
}
