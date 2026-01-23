package com.achiever.strava;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaActivityResponse {
    private Long id;
    private String name;

    @JsonProperty("sport_type")
    private String sportType;

    @JsonProperty("type")
    private String type;

    @JsonProperty("start_date")
    private OffsetDateTime startDate;

    @JsonProperty("distance")
    private Double distance;

    @JsonProperty("moving_time")
    private Integer movingTime;

    @JsonProperty("elapsed_time")
    private Integer elapsedTime;
}
