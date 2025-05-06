// functions/ingestor/src/main/java/dev/devanks/solarman/ingestor/service/IngestorService.java
package dev.devanks.solarman.ingestor.service;

import dev.devanks.solarman.ingestor.client.SolarmanApiClient;
import dev.devanks.solarman.ingestor.exception.IngestionException;
import dev.devanks.solarman.ingestor.mapper.HistoryReadingMapper;
import dev.devanks.solarman.ingestor.mapper.LatestReadingMapper;
import dev.devanks.solarman.ingestor.model.IngestionResult;
import dev.devanks.solarman.ingestor.model.SolarmanAPIResponse;
import dev.devanks.solarman.ingestor.repository.LatestSolarReadingRepository;
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

    private final SolarmanApiClient solarmanApiClient;
    private final LatestSolarReadingRepository latestReadingRepository;
    private final SolarReadingHistoryRepository historyRepository;
    private final LatestReadingMapper latestMapper;
    private final HistoryReadingMapper historyMapper;

    /**
     * Perform the ingestion process:
     * 1. Call Solarman API
     * 2. Map API response to entities
     * 3. Save entities to Firestore
     * 4. Return result object
     *
     * @return IngestionResult object containing status and details
     */
    public IngestionResult performIngestion() {
        log.info("Starting Solarman data ingestion process.");
        Instant start = Instant.now();

        try {
            var apiResponse = getSolarmanAPIResponse();

            // 2. Map API response to entities
            var ingestedTimestamp = Instant.now();
            var latestEntity = latestMapper.mapToEntity(apiResponse, ingestedTimestamp);
            var historyEntity = historyMapper.mapToEntity(apiResponse, ingestedTimestamp);
            var latestDocId = latestEntity.getId();
            var historyDocId = historyEntity.getId();

            // 3. Save entities to Firestore
            log.info("Saving latest reading (ID: {})", latestDocId);
            Mono<Void> saveOperation = latestReadingRepository.save(latestEntity)
                    .doOnSuccess(saved -> log.info("Successfully saved latest reading ID: {}", saved.getId()))
                    .doOnError(e -> log.error("Failed to save latest reading ID: {}", latestDocId, e))
                    .then(Mono.defer(() -> {
                        log.info("Saving history reading (ID: {})", historyDocId);
                        return historyRepository.save(historyEntity)
                                .doOnSuccess(saved -> log.info("Successfully saved history reading ID: {}", saved.getId()))
                                .doOnError(e -> log.error("Failed to save history reading ID: {}", historyDocId, e));
                    }))
                    .then();

            // 4. Block and Build Success Result
            saveOperation.block(); // block() will throw if reactive chain had an error

            return getIngestionResult(start, latestDocId, historyDocId);

        } catch (Exception e) {
            log.error("Ingestion process failed: {}", e.getMessage(), e);
            return IngestionResult.builder()
                    .status(IngestionResult.Status.FAILURE)
                    .message("Ingestion failed: " + e.getMessage())
                    .errorDetails(e.getClass().getName() + ": " + e.getMessage()) // Include exception type
                    .build();
        }
    }

    private static IngestionResult getIngestionResult(Instant start, String latestDocId, String historyDocId) {
        long duration = ChronoUnit.MILLIS.between(start, Instant.now());

        var ingestionResult = IngestionResult.builder()
                .status(IngestionResult.Status.SUCCESS)
                .message("Ingestion successful (mapped & saved)")
                .durationMs(duration)
                .latestDocumentId(latestDocId)
                .historyDocumentId(historyDocId)
                .build();
        log.info("Ingestion result: {}", ingestionResult);
        return ingestionResult;
    }

    /**
     * Calls the Solarman API and returns the response.
     *
     * @return SolarmanAPIResponse object containing the API data
     */
    private SolarmanAPIResponse getSolarmanAPIResponse() {
        // 1. Call Solarman API
        try {
            log.info("Calling Solarman API via Feign client.");
            var apiResponse = solarmanApiClient.getSystemData();
            if (apiResponse == null) {
                throw new IngestionException("Received null response from Solarman API (via Feign)");
            }
            log.info("Successfully received response from Solarman API.");
            log.debug("API Response Data: {}", apiResponse);
            return apiResponse;
        } catch (FeignException e) {
            log.error("Solarman API call failed (Feign): Status={}, Body={}", e.status(), e.contentUTF8(), e);
            throw new IngestionException("Solarman API call failed (Feign)", e);
        }
    }
}
