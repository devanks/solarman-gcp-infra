package dev.devanks.solarman.archiver.service;

import dev.devanks.solarman.archiver.entity.SolarReadingDailyEntity;
import dev.devanks.solarman.archiver.entity.SolarReadingHistoryEntity;
import dev.devanks.solarman.archiver.repository.SolarReadingDailyRepository;
import dev.devanks.solarman.archiver.repository.SolarReadingHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static java.time.ZoneOffset.UTC;
import static java.util.stream.Collectors.groupingBy;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirestoreService {

    private final SolarReadingHistoryRepository historyRepository;
    private final SolarReadingDailyRepository dailyRepository;

    /**
     * Fetches all documents with readingTimestamp strictly between the start and end timestamps.
     * All filtering is done client-side after fetching all documents.
     *
     * @param startTimestamp The exclusive start timestamp.
     * @param endTimestamp   The exclusive end timestamp.
     * @return A Flux emitting all matching entities.
     */
    public Flux<SolarReadingHistoryEntity> fetchAllDocumentsBetween(Instant startTimestamp, Instant endTimestamp) {
        log.info("Attempting to fetch documents with readingTimestamp after {} and before {}", startTimestamp, endTimestamp);
        return historyRepository.findAll()
                .filter(entity -> entity.getReadingTimestamp() != null &&
                        entity.getReadingTimestamp().isAfter(startTimestamp) &&
                        entity.getReadingTimestamp().isBefore(endTimestamp))
                .doOnSubscribe(s -> log.debug("Subscribed to fetch documents between {} and {}", startTimestamp, endTimestamp))
                .doOnNext(entity -> log.trace("Filtered entity (between {} and {}): ID {}", startTimestamp, endTimestamp, entity.getId()))
                .doOnComplete(() -> log.debug("Completed fetching documents between {} and {}", startTimestamp, endTimestamp))
                .doOnError(e -> log.error("Error fetching documents between {} and {}: {}", startTimestamp, endTimestamp, e.getMessage(), e));
    }

    /**
     * Deletes a list of entities from Firestore.
     *
     * @param entitiesToDelete The list of entities to delete.
     * @return A Mono that completes when deletion is done.
     */
    public Mono<Void> deleteEntities(List<SolarReadingHistoryEntity> entitiesToDelete) {
        if (entitiesToDelete == null || entitiesToDelete.isEmpty()) {
            log.debug("No entities provided for deletion.");
            return Mono.empty();
        }
        log.debug("Attempting to delete {} entities from Firestore.", entitiesToDelete.size());
        return historyRepository.deleteAll(entitiesToDelete)
                .doOnSuccess(v -> log.info("Successfully deleted {} entities from Firestore.", entitiesToDelete.size()))
                .doOnError(e -> log.error("Failed to delete {} entities from Firestore: {}", entitiesToDelete.size(), e.getMessage(), e));
    }

    /**
     * Convert the list of entities to daily stats and save them to Firestore.
     *
     * @param fetchedEntities The list of entities to save.
     */
    public void saveDailyStats(List<SolarReadingHistoryEntity> fetchedEntities) {
        if (fetchedEntities == null || fetchedEntities.isEmpty()) {
            log.debug("No entities provided for daily stats.");
            return;
        }
        log.debug("Attempting to save {} daily stats to Firestore.", fetchedEntities.size());

        List<SolarReadingDailyEntity> dailyStats = fetchedEntities.stream()
                .collect(groupingBy(
                        entity -> entity.getReadingTimestamp().atZone(UTC).toLocalDate()
                ))
                .entrySet().stream()
                .map(entry -> {
                    var date = entry.getKey();
                    var entities = entry.getValue();
                    return getSolarReadingDailyEntity(entities, date);
                })
                .toList();

        dailyRepository.saveAll(dailyStats)
                .subscribe(
                        entity -> log.info("Successfully saved daily stats: {}", entity),
                        error -> log.error("Error saving daily stats: {}", error.getMessage(), error),
                        () -> log.info("Completed saving daily stats.")
                );
    }

    /**
     * Convert a Flux of entities to daily stats and save them to Firestore in a fully reactive way.
     *
     * @param entityFlux The Flux of entities to save.
     * @return a Mono that completes when all daily stats are saved
     */
    public Mono<Void> saveDailyStats(Flux<SolarReadingHistoryEntity> entityFlux) {
        return entityFlux
            .groupBy(entity -> entity.getReadingTimestamp().atZone(UTC).toLocalDate())
            .flatMap(groupedFlux -> groupedFlux.collectList().map(entities -> {
                var date = groupedFlux.key();
                return getSolarReadingDailyEntity(entities, date);
            }))
            .as(dailyRepository::saveAll)
            .doOnNext(entity -> log.info("Successfully saved daily stats: {}", entity))
            .doOnError(error -> log.error("Error saving daily stats: {}", error.getMessage(), error))
            .then();
    }

    private SolarReadingDailyEntity getSolarReadingDailyEntity(List<SolarReadingHistoryEntity> entities, LocalDate date) {
        double maxProd = entities.stream().mapToDouble(SolarReadingHistoryEntity::getDailyProductionKWh).max().orElse(0.0);
        double avgPower = entities.stream().mapToDouble(SolarReadingHistoryEntity::getCurrentPowerW).average().orElse(0.0);
        double maxPower = entities.stream().mapToDouble(SolarReadingHistoryEntity::getCurrentPowerW).max().orElse(0.0);
        return SolarReadingDailyEntity.builder()
                .id(date.toString())
                .date(date.atStartOfDay().toInstant(UTC))
                .dailyProductionKWh(maxProd)
                .averagePowerW(avgPower)
                .maxPowerH(maxPower)
                .build();
    }
}
