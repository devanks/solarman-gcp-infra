// functions/ingestor/src/main/java/dev/devanks/solarman/ingestor/mapper/LatestReadingMapper.java
package dev.devanks.solarman.ingestor.mapper;

import dev.devanks.solarman.ingestor.config.IngestorProperties;
import dev.devanks.solarman.ingestor.entity.LatestSolarReadingEntity;
import dev.devanks.solarman.ingestor.model.SolarmanAPIResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class LatestReadingMapper {

    private final IngestorProperties properties;

    public LatestSolarReadingEntity mapToEntity(SolarmanAPIResponse response, Instant ingestedTimestamp) {
        Instant readingTimestamp = parseReadingTimestamp(response, ingestedTimestamp);
        boolean isOnline = "NORMAL".equalsIgnoreCase(response.getNetworkStatus());
        double dailyKWh = (response.getGenerationValue() != null) ? response.getGenerationValue() : 0.0;
        double currentW = (response.getGenerationPower() != null) ? response.getGenerationPower() : 0.0;

        return LatestSolarReadingEntity.builder()
                .id(properties.getGcp().getFirestoreLatestDocumentId()) // Use fixed ID from properties
                .dailyProductionKWh(dailyKWh)
                .currentPowerW(currentW)
                .isOnline(isOnline)
                .readingTimestamp(readingTimestamp)
                .ingestedTimestamp(ingestedTimestamp)
                .build();
    }

    // --- Helper to parse timestamp (copied from service) ---
    private Instant parseReadingTimestamp(SolarmanAPIResponse response, Instant defaultTimestamp) {
        if (response.getLastUpdateTime() != null && response.getLastUpdateTime() > 0) {
            try {
                // Assuming lastUpdateTime is epoch seconds
                return Instant.ofEpochSecond(response.getLastUpdateTime());
            } catch (Exception e) {
                log.warn("Failed to parse API timestamp {}, using default ({}) instead.", response.getLastUpdateTime(), defaultTimestamp, e);
            }
        } else {
            log.warn("API timestamp missing or invalid, using default ({}) for readingTimestamp.", defaultTimestamp);
        }
        return defaultTimestamp;
    }
}
