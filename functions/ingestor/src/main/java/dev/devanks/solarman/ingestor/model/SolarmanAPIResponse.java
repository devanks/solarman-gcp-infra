// functions/ingestor/src/main/java/dev/devanks/solarman/ingestor/model/SolarmanAPIResponse.java
package dev.devanks.solarman.ingestor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data // Generates getters, setters, toString, equals, hashCode
@NoArgsConstructor // Generates no-args constructor
@AllArgsConstructor // Generates all-args constructor
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore fields in JSON not defined here
public class SolarmanAPIResponse {

    @JsonProperty("systemId")
    private Integer systemId;

    @JsonProperty("generationValue")
    private Double generationValue; // Daily Production (kWh)

    @JsonProperty("generationUploadTotal")
    private Double generationUploadTotal;

    @JsonProperty("generationPower")
    private Double generationPower; // Instantaneous Power (W)

    @JsonProperty("networkStatus")
    private String networkStatus;   // Online Status ("NORMAL", "ALL_OFFLINE", etc.)

    @JsonProperty("networkStatusRealtime")
    private String networkStatusRealtime;

    @JsonProperty("warningStatus")
    private String warningStatus;

    @JsonProperty("lastUpdateTime")
    private Long lastUpdateTime; // Unix timestamp (seconds since epoch)

    @JsonProperty("acceptDay")
    private String acceptDay;      // Date string?

    @JsonProperty("genStatus")
    private String genStatus;

    @JsonProperty("genTotalActual")
    private Double genTotalActual;

    // Add other fields if needed based on actual API response
}
