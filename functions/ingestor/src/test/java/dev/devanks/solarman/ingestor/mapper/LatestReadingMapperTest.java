// functions/ingestor/src/test/java/dev/devanks/solarman/ingestor/mapper/LatestReadingMapperTest.java
package dev.devanks.solarman.ingestor.mapper;

import dev.devanks.solarman.ingestor.config.IngestorProperties;
import dev.devanks.solarman.ingestor.entity.LatestSolarReadingEntity;
import dev.devanks.solarman.ingestor.model.SolarmanAPIResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LatestReadingMapperTest {

    @Mock
    private IngestorProperties mockProperties;
    @Mock
    private IngestorProperties.GcpProperties mockGcpProperties;

    @InjectMocks
    private LatestReadingMapper latestReadingMapper;

    private static final String FIXED_LATEST_ID = "the-latest-one";
    private static final long API_TIMESTAMP_SECONDS = 1678886400L; // Example timestamp

    @BeforeEach
    void setUp() {
        // Link nested mock properties for the mapper
        when(mockProperties.getGcp()).thenReturn(mockGcpProperties);
        when(mockGcpProperties.getFirestoreLatestDocumentId()).thenReturn(FIXED_LATEST_ID);
    }

    @Test
    @DisplayName("mapToEntity - Success Path")
    void mapToEntity_Success() {
        // Arrange
        Instant now = Instant.now();
        SolarmanAPIResponse apiResponse = SolarmanAPIResponse.builder()
                .generationValue(25.5)
                .generationPower(3100.0)
                .networkStatus("NORMAL")
                .lastUpdateTime(API_TIMESTAMP_SECONDS)
                .build();

        // Act
        LatestSolarReadingEntity entity = latestReadingMapper.mapToEntity(apiResponse, now);

        // Assert
        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isEqualTo(FIXED_LATEST_ID);
        assertThat(entity.getDailyProductionKWh()).isEqualTo(25.5);
        assertThat(entity.getCurrentPowerW()).isEqualTo(3100.0);
        assertThat(entity.isOnline()).isTrue();
        assertThat(entity.getReadingTimestamp()).isEqualTo(Instant.ofEpochSecond(API_TIMESTAMP_SECONDS));
        assertThat(entity.getIngestedTimestamp()).isEqualTo(now);
    }

    @Test
    @DisplayName("mapToEntity - Handles Null API Data")
    void mapToEntity_HandlesNullData() {
        // Arrange
        Instant now = Instant.now();
        SolarmanAPIResponse apiResponse = SolarmanAPIResponse.builder().build(); // All fields null

        // Act
        LatestSolarReadingEntity entity = latestReadingMapper.mapToEntity(apiResponse, now);

        // Assert
        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isEqualTo(FIXED_LATEST_ID);
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
                .lastUpdateTime(-1L) // Invalid timestamp
                .build();

        // Act
        LatestSolarReadingEntity entity = latestReadingMapper.mapToEntity(apiResponse, now);

        // Assert
        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isEqualTo(FIXED_LATEST_ID);
        assertThat(entity.getReadingTimestamp()).isEqualTo(now); // Should default to ingested time
        assertThat(entity.getIngestedTimestamp()).isEqualTo(now);
    }
}
