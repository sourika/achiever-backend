package com.achiever.strava;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaAthleteResponse {
    private Long id;
    private String username;
    private String firstname;
    private String lastname;
    private String email;

    @JsonProperty("profile")
    private String profileImageUrl;
}
