// functions/ingestor/src/test/java/dev/devanks/solarman/ingestor/mapper/HistoryReadingMapperTest.java
package dev.devanks.solarman.ingestor.mapper;

import dev.devanks.solarman.ingestor.entity.SolarReadingHistoryEntity;
import dev.devanks.solarman.ingestor.model.SolarmanAPIResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryReadingMapperTest {

    private final HistoryReadingMapper historyReadingMapper = new HistoryReadingMapper(); // No dependencies

    private static final long API_TIMESTAMP_SECONDS = 1678886400L; // Example timestamp

    @Test
    @DisplayName("mapToEntity - Success Path")
    void mapToEntity_Success() {
        // Arrange
        Instant now = Instant.now();
        Instant expectedReadingTime = Instant.ofEpochSecond(API_TIMESTAMP_SECONDS);
        SolarmanAPIResponse apiResponse = SolarmanAPIResponse.builder()
                .generationValue(25.5)
                .generationPower(3100.0)
                .networkStatus("NORMAL")
                .lastUpdateTime(API_TIMESTAMP_SECONDS)
                .build();

        // Act
        SolarReadingHistoryEntity entity = historyReadingMapper.mapToEntity(apiResponse, now);

        // Assert
        assertThat(entity).isNotNull();
        // Verify ID format based on timestamp
        assertThat(entity.getId())
                .startsWith(String.valueOf(expectedReadingTime.toEpochMilli()))
                .hasSizeGreaterThan(String.valueOf(expectedReadingTime.toEpochMilli()).length());
        assertThat(entity.getDailyProductionKWh()).isEqualTo(25.5);
        assertThat(entity.getCurrentPowerW()).isEqualTo(3100.0);
        assertThat(entity.isOnline()).isTrue();
        assertThat(entity.getReadingTimestamp()).isEqualTo(expectedReadingTime);
        assertThat(entity.getIngestedTimestamp()).isEqualTo(now);
    }

    @Test
    @DisplayName("mapToEntity - Handles Null API Data")
    void mapToEntity_HandlesNullData() {
        // Arrange
        Instant now = Instant.now();
        SolarmanAPIResponse apiResponse = SolarmanAPIResponse.builder().build(); // All fields null

        // Act
        SolarReadingHistoryEntity entity = historyReadingMapper.mapToEntity(apiResponse, now);

        // Assert
        assertThat(entity).isNotNull();
        // ID should be based on 'now' timestamp
        assertThat(entity.getId())
                .startsWith(String.valueOf(now.toEpochMilli()))
                .hasSizeGreaterThan(String.valueOf(now.toEpochMilli()).length());
        assertThat(entity.getDailyProductionKWh()).isEqualTo(0.0);
        assertThat(entity.getCurrentPowerW()).isEqualTo(0.0);
        assertThat(entity.isOnline()).isFalse(); // Null status maps to false
        assertThat(entity.getReadingTimestamp()).isEqualTo(now); // Defaults to ingested timestamp
        assertThat(entity.getIngestedTimestamp()).isEqualTo(now);
    }

    @Test
    @DisplayName("mapToEntity - Handles Invalid Timestamp")
    void mapToEntity_HandlesInvalidTimestamp() {
        // Arrange
        Instant now = Instant.now();
        SolarmanAPIResponse apiResponse = SolarmanAPIResponse.builder()
                .generationValue(1.0)
                .generationPower(100.0)
                .networkStatus("NORMAL")
                .lastUpdateTime(0L) // Invalid timestamp
                .build();

        // Act
        SolarReadingHistoryEntity entity = historyReadingMapper.mapToEntity(apiResponse, now);

        // Assert
        assertThat(entity).isNotNull();
        // ID should be based on 'now' timestamp
        assertThat(entity.getId())
                .startsWith(String.valueOf(now.toEpochMilli()))
                .hasSizeGreaterThan(String.valueOf(now.toEpochMilli()).length());
        assertThat(entity.getReadingTimestamp()).isEqualTo(now); // Should default to ingested time
        assertThat(entity.getIngestedTimestamp()).isEqualTo(now);
    }
}

