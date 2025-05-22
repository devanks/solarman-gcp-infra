package dev.devanks.solarman.archiver.service;

import dev.devanks.solarman.archiver.config.ArchiveProperties;
import dev.devanks.solarman.archiver.entity.SolarReadingHistoryEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static java.time.ZoneOffset.UTC;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArchiveService Unit Tests")
class ArchiveServiceTest {

    @Mock
    private FirestoreService mockFirestoreService;
    @Mock
    private StorageWriterService mockStorageWriterService;
    @Mock
    private ArchiveProperties mockArchiveProperties;

    @InjectMocks
    private ArchiveService archiveService;

    private static final String MOCK_GCS_PATH = "gs://bucket/archive.csv.gz";
    private static final int DEFAULT_DAYS_OLD = 7;

    private List<SolarReadingHistoryEntity> createMockEntities(int count) {
        if (count == 0) return Collections.emptyList();
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> SolarReadingHistoryEntity.builder()
                        .id("id" + i)
                        .readingTimestamp(Instant.now())
                        .build())
                .toList();
    }

    // --- Test for processFetchedEntities (logic largely unchanged, check params) ---
    @Test
    @DisplayName("processFetchedEntities: when entity list is empty, returns 'No data' summary")
    void processFetchedEntities_emptyList_returnsNoDataSummary() {
        List<SolarReadingHistoryEntity> emptyList = Collections.emptyList();
        LocalDate archiveForDate = LocalDate.of(2023, 10, 20);
        Instant effectiveCutoffForLog = archiveForDate.plusDays(1).atStartOfDay(UTC).toInstant();

        Mono<String> result = archiveService.processFetchedEntities(emptyList, effectiveCutoffForLog, archiveForDate);

        StepVerifier.create(result)
                .expectNextMatches(summary -> summary.contains("No data to archive.") && summary.contains("Documents fetched: 0"))
                .verifyComplete();
        verifyNoInteractions(mockStorageWriterService);
    }

    // --- Tests for performDefaultArchival (Uses fetchAllDocumentsBetween) ---
    @Test
    @DisplayName("performDefaultArchival: full flow - fetch, GCS success, deletion enabled & success")
    void performDefaultArchival_fullFlow_allSuccess_deletionEnabled() {
        // Arrange
        when(mockArchiveProperties.getDaysOld()).thenReturn(DEFAULT_DAYS_OLD); // Use constant
        LocalDate archiveForDate = LocalDate.now(UTC);
        Instant inclusiveStartTimestamp = archiveForDate.minusDays(DEFAULT_DAYS_OLD)
                .atStartOfDay(UTC)
                .toInstant();
        Instant expectedExclusiveEnd = archiveForDate.plusDays(1)
                .atStartOfDay(UTC)
                .toInstant();

        List<SolarReadingHistoryEntity> entities = createMockEntities(5);

        when(mockFirestoreService.fetchAllDocumentsBetween(inclusiveStartTimestamp, expectedExclusiveEnd))
                .thenReturn(Flux.fromIterable(entities));
        when(mockStorageWriterService.archiveEntitiesToGCS(entities, archiveForDate))
                .thenReturn(Mono.just(MOCK_GCS_PATH));
        when(mockArchiveProperties.isDeletionEnabled()).thenReturn(true);
        when(mockFirestoreService.deleteEntities(entities)).thenReturn(Mono.empty());

        // Act
        Mono<String> result = archiveService.performDefaultArchival();

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(summary ->
                        summary.contains("Success (Data Deleted).") &&
                                summary.contains("Documents fetched: 5") &&
                                summary.contains("Successfully archived to GCS (CSV): 5") &&
                                summary.contains("Successfully deleted from Firestore: 5") &&
                                summary.contains("Archival process for GCS date " + archiveForDate) // Changed
                )
                .verifyComplete();

        verify(mockArchiveProperties).getDaysOld();
        verify(mockFirestoreService).fetchAllDocumentsBetween(inclusiveStartTimestamp, expectedExclusiveEnd);
        verify(mockStorageWriterService).archiveEntitiesToGCS(entities, archiveForDate);
        verify(mockFirestoreService).deleteEntities(entities);
    }

    @Test
    @DisplayName("performDefaultArchival: fetch yields no data")
    void performDefaultArchival_fetchYieldsNoData() {
        when(mockArchiveProperties.getDaysOld()).thenReturn(DEFAULT_DAYS_OLD);
        var expectedArchiveDate = LocalDate.now(UTC);
        var inclusiveStartTimestamp = expectedArchiveDate.minusDays(DEFAULT_DAYS_OLD)
                .atStartOfDay(UTC)
                .toInstant();
        var expectedExclusiveEnd = expectedArchiveDate.plusDays(1)
                .atStartOfDay(UTC)
                .toInstant();

        when(mockFirestoreService.fetchAllDocumentsBetween(inclusiveStartTimestamp, expectedExclusiveEnd))
                .thenReturn(Flux.empty());

        var result = archiveService.performDefaultArchival();

        StepVerifier.create(result)
                .expectNextMatches(summary -> summary.contains("No data to archive.") && summary.contains("Documents fetched: 0"))
                .verifyComplete();

        verify(mockFirestoreService).fetchAllDocumentsBetween(inclusiveStartTimestamp, expectedExclusiveEnd);
        verifyNoInteractions(mockStorageWriterService);
    }


    // --- Tests for archiveOlderThan (Uses fetchAllDocumentsBetween with Instant.EPOCH) ---
    @Test
    @DisplayName("archiveOlderThan: full flow - fetch, GCS success, deletion enabled & success")
    void archiveOlderThan_fullFlow_allSuccess_deletionEnabled() {

        var entities = createMockEntities(3);
        var logicalArchiveForDate = LocalDate.of(2023, 10, 25);
        var exclusiveEndTimestamp = logicalArchiveForDate.plusDays(1).atStartOfDay(UTC).toInstant();
        var inclusiveStartTimestamp = logicalArchiveForDate.minusDays(DEFAULT_DAYS_OLD)
                .atStartOfDay(UTC)
                .toInstant();


        when(mockArchiveProperties.getDaysOld()).thenReturn(DEFAULT_DAYS_OLD);
        when(mockFirestoreService.fetchAllDocumentsBetween(inclusiveStartTimestamp, exclusiveEndTimestamp))
                .thenReturn(Flux.fromIterable(entities));
        when(mockStorageWriterService.archiveEntitiesToGCS(entities, logicalArchiveForDate))
                .thenReturn(Mono.just(MOCK_GCS_PATH));
        when(mockArchiveProperties.isDeletionEnabled()).thenReturn(true);
        when(mockFirestoreService.deleteEntities(entities)).thenReturn(Mono.empty());

        Mono<String> result = archiveService.archiveOlderThan(logicalArchiveForDate);

        StepVerifier.create(result)
                .expectNextMatches(summary ->
                        summary.contains("Success (Data Deleted).") &&
                                summary.contains("Documents fetched: 3") &&
                                summary.contains("Successfully archived to GCS (CSV): 3") &&
                                summary.contains("Successfully deleted from Firestore: 3") &&
                                summary.contains("Archival process for GCS date " + logicalArchiveForDate) // Changed from "date" to "GCS date"
                )
                .verifyComplete();

        verify(mockArchiveProperties).getDaysOld(); // Verify it was called
        verify(mockFirestoreService).fetchAllDocumentsBetween(inclusiveStartTimestamp, exclusiveEndTimestamp);
        verify(mockStorageWriterService).archiveEntitiesToGCS(entities, logicalArchiveForDate);
        verify(mockFirestoreService).deleteEntities(entities);
    }

    @Test
    @DisplayName("archiveOlderThan: full flow - deletion DISABLED")
    void archiveOlderThan_fullFlow_deletionDisabled() {

        var entities = createMockEntities(3);
        var logicalArchiveForDate = LocalDate.of(2023, 10, 25);
        var exclusiveEndTimestamp = logicalArchiveForDate.plusDays(1).atStartOfDay(UTC).toInstant();
        var inclusiveStartTimestamp = logicalArchiveForDate.minusDays(DEFAULT_DAYS_OLD)
                .atStartOfDay(UTC)
                .toInstant();

        when(mockArchiveProperties.getDaysOld()).thenReturn(DEFAULT_DAYS_OLD);
        when(mockFirestoreService.fetchAllDocumentsBetween(inclusiveStartTimestamp, exclusiveEndTimestamp))
                .thenReturn(Flux.fromIterable(entities));
        when(mockStorageWriterService.archiveEntitiesToGCS(entities, logicalArchiveForDate))
                .thenReturn(Mono.just(MOCK_GCS_PATH));
        when(mockArchiveProperties.isDeletionEnabled()).thenReturn(false);

        Mono<String> result = archiveService.archiveOlderThan(logicalArchiveForDate);

        StepVerifier.create(result)
                .expectNextMatches(summary ->
                        summary.contains("Success (Deletion Disabled).") &&
                                summary.contains("Documents fetched: 3") &&
                                summary.contains("Successfully archived to GCS (CSV): 3") &&
                                summary.contains("Successfully deleted from Firestore: 0") &&
                                summary.contains("Archival process for GCS date " + logicalArchiveForDate) // Changed
                )
                .verifyComplete();

        verify(mockArchiveProperties).getDaysOld();
        verify(mockFirestoreService).fetchAllDocumentsBetween(inclusiveStartTimestamp, exclusiveEndTimestamp);
        verify(mockStorageWriterService).archiveEntitiesToGCS(entities, logicalArchiveForDate);
        verify(mockFirestoreService, never()).deleteEntities(any());
    }

    @Test
    @DisplayName("archiveOlderThan: fetch fails")
    void archiveOlderThan_fetchFails() {

        var logicalArchiveForDate = LocalDate.of(2023, 10, 25);
        var exclusiveEndTimestamp = logicalArchiveForDate.plusDays(1).atStartOfDay(UTC).toInstant();
        var inclusiveStartTimestamp = logicalArchiveForDate.minusDays(DEFAULT_DAYS_OLD)
                .atStartOfDay(UTC)
                .toInstant();

        RuntimeException fetchError = new RuntimeException("Simulated fetch error");

        when(mockArchiveProperties.getDaysOld()).thenReturn(DEFAULT_DAYS_OLD); // Even if fetch fails, daysOld is read
        when(mockFirestoreService.fetchAllDocumentsBetween(inclusiveStartTimestamp, exclusiveEndTimestamp))
                .thenReturn(Flux.error(fetchError));

        Mono<String> result = archiveService.archiveOlderThan(logicalArchiveForDate);

        StepVerifier.create(result)
                .expectNextMatches(summary -> summary.contains("Fetch error.") &&
                        summary.contains(fetchError.getMessage()) &&
                        summary.contains("Archival for GCS date " + logicalArchiveForDate) // Changed
                )
                .verifyComplete();

        verify(mockArchiveProperties).getDaysOld(); // Verify it was called
        verify(mockFirestoreService).fetchAllDocumentsBetween(inclusiveStartTimestamp, exclusiveEndTimestamp);
        verifyNoInteractions(mockStorageWriterService);
    }


    // --- Tests for individual helper/error handling methods (can remain mostly the same) ---

    /**
     * Tests for handleFetchError
     */
    @Test
    @DisplayName("handleGcsWriteError: returns 'GCS archival failed' summary")
    void handleGcsWriteError_returnsGcsFailedSummary() {
        Throwable gcsException = new IOException("Network timeout");
        LocalDate archiveForDate = LocalDate.of(2023, 10, 20);
        Mono<String> result = archiveService.handleGcsWriteError(gcsException, 10, archiveForDate);

        StepVerifier.create(result)
                .expectNextMatches(summary ->
                        summary.contains("GCS archival failed.") &&
                                summary.contains("Documents fetched: 10") &&
                                summary.contains("Successfully archived to GCS (CSV): 0")
                )
                .verifyComplete();
    }

    /**
     * Tests for handleFirestoreDeleteError
     */
    @Test
    @DisplayName("handleFirestoreDeleteError: returns 'Deletion failed' summary")
    void handleFirestoreDeleteError_returnsDeletionFailedSummary() {
        // Arrange
        Throwable deletionException = new RuntimeException("Simulated deletion error");
        long archivedCount = 5;
        long entitiesAttemptedToDeleteCount = 5;
        LocalDate archiveForDate = LocalDate.of(2023, 10, 25);
        String gcsPath = "gs://bucket/archive.csv.gz";

        // Act
        Mono<String> result = archiveService.handleFirestoreDeleteError(deletionException, archivedCount, entitiesAttemptedToDeleteCount, archiveForDate, gcsPath);

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(summary -> summary.contains("Deletion failed.") &&
                        summary.contains("Documents fetched: 5") &&
                        summary.contains("Successfully archived to GCS (CSV): 5") &&
                        summary.contains("Successfully deleted from Firestore: 0"))
                .verifyComplete();
    }

    /**
     * Tests for handleFetchError
     */
    @ParameterizedTest
    @DisplayName("orchestrateArchivalForRange: Fails when inclusiveStartTimestamp is after or equal to exclusiveEndTimestamp")
    @CsvSource({
            "2023-10-25T10:00:00Z, 2023-10-25T09:00:00Z",
            "2023-10-25T10:00:00Z, 2023-10-25T10:00:00Z"
    })
    void orchestrateArchivalForRange_invalidRange(String inclusiveStart, String exclusiveEnd) {
        // Arrange
        Instant inclusiveStartTimestamp = Instant.parse(inclusiveStart);
        Instant exclusiveEndTimestamp = Instant.parse(exclusiveEnd);
        LocalDate logicalArchiveDate = LocalDate.of(2023, 10, 25);

        // Act
        Mono<String> result = archiveService.orchestrateArchivalForRange(inclusiveStartTimestamp, exclusiveEndTimestamp, logicalArchiveDate);

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(summary -> summary.contains("Skipped due to invalid date range") &&
                        summary.contains("Documents fetched: 0") &&
                        summary.contains("Successfully archived to GCS (CSV): 0") &&
                        summary.contains("Successfully deleted from Firestore: 0"))
                .verifyComplete();
    }

    // Add more tests for other helper methods like handleDeletionAfterGcsSuccess, buildSummaryAfterSuccessfulDeletion etc.
    // if their logic is complex or has many branches. For now, they are straightforward.

    /**
     * Tests for handleDeletionAfterGcsSuccess
     */
    @Test
    @DisplayName("handleDeletionAfterGcsSuccess: when deletion is DISABLED")
    void handleDeletionAfterGcsSuccess_deletionDisabled() {
        List<SolarReadingHistoryEntity> entities = createMockEntities(2);
        long archivedCount = entities.size();
        LocalDate archiveForDate = LocalDate.of(2023, 10, 20);
        ;

        when(mockArchiveProperties.isDeletionEnabled()).thenReturn(false);

        Mono<String> result = archiveService.handleDeletionAfterGcsSuccess(entities, archivedCount, archiveForDate, MOCK_GCS_PATH);

        StepVerifier.create(result)
                .expectNextMatches(summary ->
                        summary.contains("Success (Deletion Disabled).") &&
                                summary.contains("Successfully archived to GCS (CSV): " + archivedCount) &&
                                summary.contains("Successfully deleted from Firestore: 0")
                )
                .verifyComplete();
        verifyNoInteractions(mockFirestoreService); // Because deleteEntities is on this mock
    }
}
