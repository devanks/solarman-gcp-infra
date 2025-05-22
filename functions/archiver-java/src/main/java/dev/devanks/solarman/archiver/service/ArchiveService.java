package dev.devanks.solarman.archiver.service;

import com.google.common.annotations.VisibleForTesting;
import dev.devanks.solarman.archiver.config.ArchiveProperties;
import dev.devanks.solarman.archiver.entity.SolarReadingHistoryEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static java.time.ZoneOffset.UTC;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArchiveService {

    private final FirestoreService firestoreService;
    private final StorageWriterService storageWriterService;
    private final ArchiveProperties properties;

    /**
     * Performs the default archival process. This archives data for a single specific day,
     * which is {@link ArchiveProperties#getDaysOld()} days ago from the current date.
     *
     * @return A Mono containing the summary of the archival process.
     */
    public Mono<String> performDefaultArchival() {
        return archiveOlderThan(LocalDate.now(UTC));
    }

    /**
     * Archives documents older than the specified cutoff timestamp. This method is a placeholder
     * and should be implemented to handle archival logic.
     *
     * @param targetArchiveDate The timestamp strictly before which documents should be archived.
     * @return A Mono containing the summary of the archival process.
     */
    public Mono<String> archiveOlderThan(LocalDate targetArchiveDate) {
        var inclusiveStartTimestamp = targetArchiveDate.minusDays(properties.getDaysOld())
                .atStartOfDay(UTC)
                .toInstant();
        var exclusiveEndTimestamp = targetArchiveDate.plusDays(1)
                .atStartOfDay(UTC)
                .toInstant();

        log.info("Performing default archival for data specifically on date {} (readingTimestamp range [{}, {}]))",
                targetArchiveDate, inclusiveStartTimestamp, exclusiveEndTimestamp);

        return orchestrateArchivalForRange(inclusiveStartTimestamp, exclusiveEndTimestamp, targetArchiveDate);
    }

    /**
     * Central orchestration logic for archiving documents within a specific time range.
     *
     * @param inclusiveStartTimestamp The inclusive start of the time range to fetch.
     * @param exclusiveEndTimestamp   The exclusive end of the time range to fetch.
     * @param logicalArchiveDate      The date used for GCS path naming and summary logging.
     * @return A Mono containing the summary of the archival process.
     */
    @VisibleForTesting
    Mono<String> orchestrateArchivalForRange(Instant inclusiveStartTimestamp, Instant exclusiveEndTimestamp,
                                             LocalDate logicalArchiveDate) {
        log.info("Orchestrating archival for range [{}, {}) for logical GCS date {}",
                inclusiveStartTimestamp, exclusiveEndTimestamp, logicalArchiveDate);

        if (inclusiveStartTimestamp.isAfter(exclusiveEndTimestamp) || inclusiveStartTimestamp.equals(exclusiveEndTimestamp)) {
            log.warn("Invalid range for archival: inclusiveStartTimestamp ({}) must be before exclusiveEndTimestamp ({}). " +
                    "Skipping archival for logical date {}.", inclusiveStartTimestamp, exclusiveEndTimestamp, logicalArchiveDate);
            return Mono.just(buildSummary(0, 0, 0, logicalArchiveDate,
                    "Skipped due to invalid date range (start not before end)."));
        }

        return firestoreService.fetchAllDocumentsBetween(inclusiveStartTimestamp, exclusiveEndTimestamp)
                .collectList()
                .doOnNext(firestoreService::saveDailyStats)
                .flatMap(fetchedEntities ->
                        processFetchedEntities(fetchedEntities, exclusiveEndTimestamp, logicalArchiveDate))
                .onErrorResume(fetchEx -> handleFetchError(fetchEx, logicalArchiveDate));
    }

    /**
     * Processes the fetched entities by archiving them to GCS and optionally deleting them from Firestore.
     *
     * @param fetchedEntities       The list of entities fetched from Firestore.
     * @param effectiveCutoffForLog The effective cutoff timestamp for logging.
     * @param archiveForDate        The date used for GCS path naming and summary logging.
     * @return A Mono containing the summary of the archival process.
     */
    @VisibleForTesting
    Mono<String> processFetchedEntities(List<SolarReadingHistoryEntity> fetchedEntities, Instant effectiveCutoffForLog,
                                        LocalDate archiveForDate) { // archiveForDate is the logicalArchiveDate
        long fetchedCount = fetchedEntities.size();
        if (fetchedEntities.isEmpty()) {
            log.info("No documents found in range (effective cutoff for log: {}) to archive for GCS date {}.",
                    effectiveCutoffForLog, archiveForDate);
            return Mono.just(buildSummary(fetchedCount, 0, 0, archiveForDate, "No data to archive."));
        }
        return addToGCSStorageBucket(fetchedEntities, effectiveCutoffForLog, archiveForDate, fetchedCount);
    }

    /**
     * Archives the fetched entities to a GCS bucket in CSV format.
     *
     * @param fetchedEntities       The list of entities to archive.
     * @param effectiveCutoffForLog The effective cutoff timestamp for logging.
     * @param archiveForDate        The date used for GCS path naming and summary logging.
     * @param fetchedCount          The number of entities fetched.
     * @return A Mono containing the summary of the archival process.
     */
    private Mono<String> addToGCSStorageBucket(List<SolarReadingHistoryEntity> fetchedEntities, Instant effectiveCutoffForLog,
                                               LocalDate archiveForDate, long fetchedCount) {
        log.info("Fetched {} documents to archive for GCS date {}. Effective cutoff for log: {}. Attempting to archive to GCS (CSV format).",
                fetchedCount, archiveForDate, effectiveCutoffForLog);
        return storageWriterService.archiveEntitiesToGCS(fetchedEntities, archiveForDate) // Uses archiveForDate for GCS path
                .flatMap(gcsPath -> handleDeletionAfterGcsSuccess(fetchedEntities, fetchedCount, archiveForDate, gcsPath))
                .onErrorResume(gcsEx -> handleGcsWriteError(gcsEx, fetchedCount, archiveForDate));
    }

    /**
     * Handles the deletion of entities from Firestore after successful archival to GCS.
     *
     * @param entitiesArchived The list of entities that were archived.
     * @param archivedCount    The number of entities archived.
     * @param archiveForDate   The date used for GCS path naming and summary logging.
     * @param gcsPath          The GCS path where the data was archived.
     * @return A Mono containing the summary of the archival process.
     */
    @VisibleForTesting
    Mono<String> handleDeletionAfterGcsSuccess(List<SolarReadingHistoryEntity> entitiesArchived, long archivedCount,
                                               LocalDate archiveForDate, String gcsPath) {
        if (!properties.isDeletionEnabled()) {
            log.info("Deletion is DISABLED. Skipping deletion of {} documents from Firestore.", entitiesArchived.size());
            return Mono.just(buildSummary(archivedCount, archivedCount, 0, archiveForDate, "Success (Deletion Disabled)."));
        }
        log.info("Deletion is ENABLED. Attempting to delete {} documents from Firestore.", entitiesArchived.size());
        return firestoreService.deleteEntities(entitiesArchived)
                .then(Mono.fromCallable(() -> buildSummaryAfterSuccessfulDeletion(archivedCount, entitiesArchived.size(), archiveForDate)))
                .onErrorResume(delEx -> handleFirestoreDeleteError(delEx, archivedCount, entitiesArchived.size(), archiveForDate, gcsPath));
    }

    /**
     * Builds a summary message after successful deletion of entities from Firestore.
     *
     * @param archivedCount  The number of entities archived.
     * @param deletedCount   The number of entities deleted.
     * @param archiveForDate The date used for GCS path naming and summary logging.
     * @return A summary message indicating the success of the archival process.
     */
    @VisibleForTesting
    String buildSummaryAfterSuccessfulDeletion(long archivedCount, long deletedCount, LocalDate archiveForDate) {
        log.info("Successfully deleted {} documents from Firestore (archived for GCS date {}).",
                deletedCount, archiveForDate);
        return buildSummary(archivedCount, archivedCount, deletedCount, archiveForDate, "Success (Data Deleted).");
    }

    /**
     * Handles errors that occur during the deletion of entities from Firestore.
     *
     * @param delEx                          The exception thrown during deletion.
     * @param archivedCount                  The number of entities archived.
     * @param entitiesAttemptedToDeleteCount The number of entities attempted to delete.
     * @param archiveForDate                 The date used for GCS path naming and summary logging.
     * @param gcsPath                        The GCS path where the data was archived.
     * @return A Mono containing the summary of the archival process.
     */
    @VisibleForTesting
    Mono<String> handleFirestoreDeleteError(Throwable delEx, long archivedCount, long entitiesAttemptedToDeleteCount,
                                            LocalDate archiveForDate, String gcsPath) {
        log.error("Failed to delete {} documents from Firestore (archived for GCS date {} to {}): {}",
                entitiesAttemptedToDeleteCount, archiveForDate, gcsPath, delEx.getMessage());
        return Mono.just(buildSummary(archivedCount, archivedCount, 0, archiveForDate, "Deletion failed."));
    }

    /**
     * Handles errors that occur during the archival process to GCS.
     *
     * @param gcsEx          The exception thrown during GCS archival.
     * @param fetchedCount   The number of entities fetched.
     * @param archiveForDate The date used for GCS path naming and summary logging.
     * @return A Mono containing the summary of the archival process.
     */
    @VisibleForTesting
    Mono<String> handleGcsWriteError(Throwable gcsEx, long fetchedCount, LocalDate archiveForDate) {
        log.error("Failed to archive {} documents for GCS date {} to GCS (CSV): {}",
                fetchedCount, archiveForDate, gcsEx.getMessage());
        return Mono.just(buildSummary(fetchedCount, 0, 0, archiveForDate, "GCS archival failed."));
    }

    /**
     * Handles errors that occur during the fetching of entities from Firestore.
     *
     * @param fetchEx        The exception thrown during fetching.
     * @param archiveForDate The date used for GCS path naming and summary logging.
     * @return A Mono containing the summary of the archival process.
     */
    @VisibleForTesting
    Mono<String> handleFetchError(Throwable fetchEx, LocalDate archiveForDate) {
        log.error("Archival for GCS date {} failed during fetch phase: {}", archiveForDate, fetchEx.getMessage(), fetchEx);
        var errorSummary = String.format(
                "Archival for GCS date %s failed during fetch: %s. Fetched: 0, Archived: 0, Deleted: 0. Status: Fetch error.",
                archiveForDate, fetchEx.getMessage()
        );
        log.info(errorSummary);
        return Mono.just(errorSummary);
    }

    /**
     * Builds a summary message for the archival process.
     *
     * @param fetched       The number of documents fetched.
     * @param archived      The number of documents archived.
     * @param deleted       The number of documents deleted.
     * @param archiveDate   The date used for GCS path naming and summary logging.
     * @param statusMessage The status message indicating the result of the archival process.
     * @return A summary message indicating the success or failure of the archival process.
     */
    private String buildSummary(long fetched, long archived, long deleted, LocalDate archiveDate, String statusMessage) {
        var resultSummary = String.format(
                "Archival process for GCS date %s completed. Status: %s Documents fetched: %d, Successfully archived to GCS (CSV): %d, Successfully deleted from Firestore: %d.",
                archiveDate, statusMessage, fetched, archived, deleted
        );
        log.info(resultSummary);
        return resultSummary;
    }
}
