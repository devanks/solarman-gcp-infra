// functions/ingestor/src/main/java/dev/devanks/solarman/ingestor/service/IngestorService.java
package dev.devanks.solarman.ingestor.service;

import dev.devanks.solarman.ingestor.client.SolarmanApiClient;
import dev.devanks.solarman.ingestor.config.IngestorProperties;
import dev.devanks.solarman.ingestor.entity.LatestSolarReadingEntity; // <<< Import Entities
import dev.devanks.solarman.ingestor.entity.SolarReadingHistoryEntity;
import dev.devanks.solarman.ingestor.exception.IngestionException;
import dev.devanks.solarman.ingestor.model.SolarmanAPIResponse;
import dev.devanks.solarman.ingestor.repository.LatestSolarReadingRepository; // <<< Import Repositories
import dev.devanks.solarman.ingestor.repository.SolarReadingHistoryRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestorService {

    private final IngestorProperties properties;
    private final SolarmanApiClient solarmanApiClient;
    private final LatestSolarReadingRepository latestReadingRepository;
    private final SolarReadingHistoryRepository historyRepository;

    public String performIngestion() {
        log.info("Starting Solarman data ingestion process.");
        Instant start = Instant.now();

        try {
            // 1. Call Solarman API via Feign Client
            log.info("Calling Solarman API via Feign client.");
            SolarmanAPIResponse apiResponse = solarmanApiClient.getSystemData();
            if (apiResponse == null) {
                throw new IngestionException("Received null response from Solarman API (via Feign)");
            }
            log.info("Successfully received response from Solarman API.");
            log.debug("API Response Data: {}", apiResponse);

            // 2. Process Response into intermediate structure (or directly map)
            Instant ingestedTimestamp = Instant.now();
            Instant readingTimestamp = parseReadingTimestamp(apiResponse, ingestedTimestamp);
            boolean isOnline = "NORMAL".equalsIgnoreCase(apiResponse.getNetworkStatus());
            double dailyKWh = (apiResponse.getGenerationValue() != null) ? apiResponse.getGenerationValue() : 0.0;
            double currentW = (apiResponse.getGenerationPower() != null) ? apiResponse.getGenerationPower() : 0.0;

            // 3. Map to Entities
            LatestSolarReadingEntity latestEntity = createLatestEntity(dailyKWh, currentW, isOnline, readingTimestamp, ingestedTimestamp);
            SolarReadingHistoryEntity historyEntity = createHistoryEntity(dailyKWh, currentW, isOnline, readingTimestamp, ingestedTimestamp);

            // 4. Save Entities using Repositories (reactively, sequentially)
            log.info("Saving latest reading (ID: {})", latestEntity.getId());
            Mono<Void> saveOperation = latestReadingRepository.save(latestEntity)
                    .doOnSuccess(saved -> log.info("Successfully saved latest reading ID: {}", saved.getId()))
                    .doOnError(e -> log.error("Failed to save latest reading ID: {}", latestEntity.getId(), e))
                    .then(Mono.defer(() -> { // Use Mono.defer to ensure history save happens after latest completes or errors
                        log.info("Saving history reading (ID: {})", historyEntity.getId());
                        return historyRepository.save(historyEntity)
                                .doOnSuccess(saved -> log.info("Successfully saved history reading ID: {}", saved.getId()))
                                .doOnError(e -> log.error("Failed to save history reading ID: {}", historyEntity.getId(), e));
                    }))
                    .then(); // Convert Mono<SolarReadingHistoryEntity> to Mono<Void>

            // Block until reactive chain completes for the Supplier function
            saveOperation.block(); // block() will throw exception if any save operation failed

            long duration = ChronoUnit.MILLIS.between(start, Instant.now());
            String successMsg = String.format("Ingestion successful (separate saves) in %d ms.", duration);
            log.info(successMsg);
            return successMsg;

        } catch (FeignException e) {
            log.error("Solarman API call failed (Feign): Status={}, Body={}", e.status(), e.contentUTF8(), e);
            throw new IngestionException("Ingestion failed: Error calling Solarman API: " + e.getMessage(), e);
        } catch (Exception e) { // Catch other exceptions (reactive block exceptions, mapping errors)
            log.error("Ingestion process failed: {}", e.getMessage(), e);
            // If block() threw an exception from the reactive chain, wrap it
            throw new IngestionException("Ingestion failed: " + e.getMessage(), e);
        }
    }

    // --- Helper to parse timestamp ---
    private Instant parseReadingTimestamp(SolarmanAPIResponse response, Instant defaultTimestamp) {
        if (response.getLastUpdateTime() != null && response.getLastUpdateTime() > 0) {
            try {
                return Instant.ofEpochSecond(response.getLastUpdateTime());
            } catch (Exception e) {
                log.warn("Failed to parse API timestamp {}, using default ({}) instead.", response.getLastUpdateTime(), defaultTimestamp, e);
            }
        } else {
            log.warn("API timestamp missing or invalid, using default ({}) for readingTimestamp.", defaultTimestamp);
        }
        return defaultTimestamp;
    }

    // --- Helper to create Latest Entity ---
    private LatestSolarReadingEntity createLatestEntity(double dailyKWh, double currentW, boolean isOnline, Instant readingTimestamp, Instant ingestedTimestamp) {
        return LatestSolarReadingEntity.builder()
                .id(properties.getGcp().getFirestoreLatestDocumentId()) // Use fixed ID from properties
                .dailyProductionKWh(dailyKWh)
                .currentPowerW(currentW)
                .isOnline(isOnline)
                .readingTimestamp(readingTimestamp)
                .ingestedTimestamp(ingestedTimestamp)
                .build();
    }

    // --- Helper to create History Entity ---
    private SolarReadingHistoryEntity createHistoryEntity(double dailyKWh, double currentW, boolean isOnline, Instant readingTimestamp, Instant ingestedTimestamp) {
        // Generate history document ID based on nanoseconds for uniqueness
        String historyDocId = readingTimestamp.toEpochMilli()
                + String.format("%06d", readingTimestamp.getNano() % 1_000_000);

        return SolarReadingHistoryEntity.builder()
                .id(historyDocId) // Use generated ID
                .dailyProductionKWh(dailyKWh)
                .currentPowerW(currentW)
                .isOnline(isOnline)
                .readingTimestamp(readingTimestamp)
                .ingestedTimestamp(ingestedTimestamp)
                .build();
    }

}
