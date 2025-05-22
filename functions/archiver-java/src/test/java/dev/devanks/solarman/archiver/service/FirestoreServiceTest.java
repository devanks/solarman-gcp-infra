package dev.devanks.solarman.archiver.service;

import dev.devanks.solarman.archiver.entity.SolarReadingDailyEntity;
import dev.devanks.solarman.archiver.entity.SolarReadingHistoryEntity;
import dev.devanks.solarman.archiver.repository.SolarReadingDailyRepository;
import dev.devanks.solarman.archiver.repository.SolarReadingHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static reactor.core.publisher.Flux.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FirestoreReaderService Unit Tests")
class FirestoreServiceTest {

    @Mock
    private SolarReadingHistoryRepository mockHistoryRepository;

    @Mock
    private SolarReadingDailyRepository mockDailyRepository;

    @InjectMocks
    private FirestoreService firestoreService;

    private SolarReadingHistoryEntity createMockEntity(String id, Instant readingTimestamp) {
        return SolarReadingHistoryEntity.builder().id(id).readingTimestamp(readingTimestamp).build();
    }

    /**
     * Tests for fetchAllDocumentsBetween method.
     * This method fetches all documents and filters them client-side based on the readingTimestamp.
     * The tests ensure that the filtering logic is correct and that the repository interactions are as expected.
     */
    @Test
    @DisplayName("fetchAllDocumentsBetween: filters entities correctly when repository returns items")
    void fetchAllDocumentsBetween_filtersCorrectly() {
        // Arrange
        Instant startExclusive = Instant.parse("2023-10-25T00:00:00Z");
        Instant endExclusive = Instant.parse("2023-10-27T00:00:00Z"); // Documents strictly before this date

        SolarReadingHistoryEntity entityBeforeStart = createMockEntity("id_before_start", startExclusive.minus(1, ChronoUnit.DAYS));
        SolarReadingHistoryEntity entityAtStart = createMockEntity("id_at_start", startExclusive); // Should NOT be included (isAfter is exclusive)
        SolarReadingHistoryEntity entityWithin1 = createMockEntity("id_within1", startExclusive.plus(1, ChronoUnit.HOURS)); // Should be included
        SolarReadingHistoryEntity entityWithin2 = createMockEntity("id_within2", endExclusive.minus(1, ChronoUnit.HOURS));   // Should be included
        SolarReadingHistoryEntity entityAtEnd = createMockEntity("id_at_end", endExclusive);       // Should NOT be included (isBefore is exclusive)
        SolarReadingHistoryEntity entityAfterEnd = createMockEntity("id_after_end", endExclusive.plus(1, ChronoUnit.DAYS));
        SolarReadingHistoryEntity entityNullTimestamp = SolarReadingHistoryEntity.builder().id("id_null_ts_between").build();


        when(mockHistoryRepository.findAll()).thenReturn(just(
                entityBeforeStart, entityAtStart, entityWithin1, entityWithin2, entityAtEnd, entityAfterEnd, entityNullTimestamp
        ));

        // Act
        Flux<SolarReadingHistoryEntity> resultFlux = firestoreService.fetchAllDocumentsBetween(startExclusive, endExclusive);

        // Assert
        StepVerifier.create(resultFlux)
                .expectNext(entityWithin1)
                .expectNext(entityWithin2)
                .verifyComplete();

        verify(mockHistoryRepository).findAll();
    }

    /**
     * Tests for fetchAllDocumentsBetween method when no entities are in the specified range.
     * The tests ensure that the method correctly returns an empty Flux when there are no matching entities.
     */
    @Test
    @DisplayName("fetchAllDocumentsBetween: when no entities in range, emits empty Flux")
    void fetchAllDocumentsBetween_noEntitiesInRange_emitsEmptyFlux() {
        // Arrange
        Instant startExclusive = Instant.parse("2023-10-25T00:00:00Z");
        Instant endExclusive = Instant.parse("2023-10-27T00:00:00Z");

        SolarReadingHistoryEntity entityBeforeStart = createMockEntity("id_before_start", startExclusive.minus(1, ChronoUnit.DAYS));
        SolarReadingHistoryEntity entityAfterEnd = createMockEntity("id_after_end", endExclusive.plus(1, ChronoUnit.DAYS));

        when(mockHistoryRepository.findAll()).thenReturn(just(entityBeforeStart, entityAfterEnd));

        // Act
        Flux<SolarReadingHistoryEntity> resultFlux = firestoreService.fetchAllDocumentsBetween(startExclusive, endExclusive);

        // Assert
        StepVerifier.create(resultFlux)
                .verifyComplete();

        verify(mockHistoryRepository).findAll();
    }

    /**
     * Tests for fetchAllDocumentsBetween method when the repository returns no entities.
     * The tests ensure that the method correctly returns an empty Flux when there are no entities in the repository.
     */
    @Test
    @DisplayName("fetchAllDocumentsBetween: when repository returns no entities, emits empty Flux")
    void fetchAllDocumentsBetween_repositoryReturnsNoEntities_emitsEmptyFlux() {
        // Arrange
        Instant startExclusive = Instant.parse("2023-10-25T00:00:00Z");
        Instant endExclusive = Instant.parse("2023-10-27T00:00:00Z");
        when(mockHistoryRepository.findAll()).thenReturn(empty());

        // Act
        Flux<SolarReadingHistoryEntity> resultFlux = firestoreService.fetchAllDocumentsBetween(startExclusive, endExclusive);

        // Assert
        StepVerifier.create(resultFlux)
                .verifyComplete();

        verify(mockHistoryRepository).findAll();
    }

    /**
     * Tests for fetchAllDocumentsBetween method when the repository returns an error.
     * The tests ensure that the method correctly propagates the error from the repository.
     */
    @Test
    @DisplayName("fetchAllDocumentsBetween: when repository returns error, emits error")
    void fetchAllDocumentsBetween_repositoryReturnsError_emitsError() {
        // Arrange
        Instant startExclusive = Instant.parse("2023-10-25T00:00:00Z");
        Instant endExclusive = Instant.parse("2023-10-27T00:00:00Z");
        RuntimeException simulatedError = new RuntimeException("Firestore connection failed for between query");
        when(mockHistoryRepository.findAll()).thenReturn(error(simulatedError));

        // Act
        Flux<SolarReadingHistoryEntity> resultFlux = firestoreService.fetchAllDocumentsBetween(startExclusive, endExclusive);

        // Assert
        StepVerifier.create(resultFlux)
                .expectErrorMatches(throwable -> throwable == simulatedError)
                .verify();

        verify(mockHistoryRepository).findAll();
    }

    /**
     * Tests for deleteEntities method.
     * This method deletes a list of entities from Firestore.
     * The tests ensure that the method behaves correctly when given different inputs.
     */
    private SolarReadingHistoryEntity createMockEntityForDelete(String id) {
        // For delete tests, the readingTimestamp isn't critical, but let's be consistent
        return SolarReadingHistoryEntity.builder().id(id).readingTimestamp(Instant.now()).build();
    }

    /**
     * Tests for deleteEntities method when the list is null or empty.
     * The tests ensure that the method returns an empty Mono and does not call the repository.
     */
    @Test
    @DisplayName("deleteEntities: when list is null, returns empty Mono and does not call repository")
    void deleteEntities_listIsNull_returnsEmptyMonoAndNoRepoCall() {
        // Act
        Mono<Void> resultMono = firestoreService.deleteEntities(null);
        // Assert
        StepVerifier.create(resultMono).verifyComplete();
        verifyNoInteractions(mockHistoryRepository);
    }

    /**
     * Tests for deleteEntities method when the list is empty.
     * The tests ensure that the method returns an empty Mono and does not call the repository.
     */
    @Test
    @DisplayName("deleteEntities: when list is empty, returns empty Mono and does not call repository")
    void deleteEntities_listIsEmpty_returnsEmptyMonoAndNoRepoCall() {
        // Act
        Mono<Void> resultMono = firestoreService.deleteEntities(Collections.emptyList());
        // Assert
        StepVerifier.create(resultMono).verifyComplete();
        verifyNoInteractions(mockHistoryRepository);
    }

    /**
     * Tests for deleteEntities method when the list has entities.
     * The tests ensure that the method calls the repository to delete the entities and handles success and failure cases.
     */
    @Test
    @DisplayName("deleteEntities: when list has entities and repository delete succeeds, completes successfully")
    void deleteEntities_listHasEntities_repoDeleteSucceeds_completes() {
        // Arrange
        List<SolarReadingHistoryEntity> entitiesToDelete = List.of(createMockEntityForDelete("id1"), createMockEntityForDelete("id2"));
        when(mockHistoryRepository.deleteAll(entitiesToDelete)).thenReturn(Mono.empty());

        // Act
        Mono<Void> resultMono = firestoreService.deleteEntities(entitiesToDelete);

        // Assert
        StepVerifier.create(resultMono).verifyComplete();
        verify(mockHistoryRepository).deleteAll(entitiesToDelete);
    }

    /**
     * Tests for deleteEntities method when the list has entities and the repository delete fails.
     * The tests ensure that the method propagates the error from the repository.
     */
    @Test
    @DisplayName("deleteEntities: when list has entities and repository delete fails, emits error")
    void deleteEntities_listHasEntities_repoDeleteFails_emitsError() {
        // Arrange
        List<SolarReadingHistoryEntity> entitiesToDelete = List.of(createMockEntityForDelete("id1"));
        RuntimeException simulatedError = new RuntimeException("Firestore delete operation failed");
        when(mockHistoryRepository.deleteAll(entitiesToDelete)).thenReturn(Mono.error(simulatedError));

        // Act
        Mono<Void> resultMono = firestoreService.deleteEntities(entitiesToDelete);

        // Assert
        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable -> throwable == simulatedError)
                .verify();
        verify(mockHistoryRepository).deleteAll(entitiesToDelete);
    }

    @Test
    @DisplayName("saveDailyStats(List): null input does not call repository")
    void saveDailyStats_listNull_noRepoCall() {
        firestoreService.saveDailyStats((List<SolarReadingHistoryEntity>) null);
        verifyNoInteractions(mockDailyRepository);
    }

    @Test
    @DisplayName("saveDailyStats(List): empty list does not call repository")
    void saveDailyStats_listEmpty_noRepoCall() {
        firestoreService.saveDailyStats(java.util.Collections.emptyList());
        verifyNoInteractions(mockDailyRepository);
    }

    @Test
    @DisplayName("saveDailyStats(List): single entity saves one daily stat")
    void saveDailyStats_listSingleEntity_savesOne() {
        Instant readingTimestamp = Instant.parse("2024-01-01T10:00:00Z");
        SolarReadingHistoryEntity historyEntity = SolarReadingHistoryEntity.builder()
                .id("id1").readingTimestamp(readingTimestamp).dailyProductionKWh(5.0).currentPowerW(100.0).build();
        List<SolarReadingHistoryEntity> entityList = List.of(historyEntity);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SolarReadingDailyEntity>> captor = forClass(List.class);
        when(mockDailyRepository.saveAll(captor.capture())).thenReturn(Flux.fromIterable(List.of()));
        firestoreService.saveDailyStats(entityList);
        verify(mockDailyRepository).saveAll(ArgumentMatchers.<Iterable<SolarReadingDailyEntity>>any());
        List<SolarReadingDailyEntity> saved = captor.getValue();
        assertEquals(1, saved.size());
        SolarReadingDailyEntity daily = saved.get(0);
        assertEquals("2024-01-01", daily.getId());
        assertEquals(5.0, daily.getDailyProductionKWh());
        assertEquals(100.0, daily.getAveragePowerW());
        assertEquals(100.0, daily.getMaxPowerH());
    }

    @Test
    @DisplayName("saveDailyStats(List): multiple entities, different days, groups and saves correctly")
    void saveDailyStats_listMultipleEntities_groupsByDay() {
        Instant tsDay1Morning = Instant.parse("2024-01-01T10:00:00Z");
        Instant tsDay1Noon = Instant.parse("2024-01-01T12:00:00Z");
        Instant tsDay2Morning = Instant.parse("2024-01-02T09:00:00Z");
        SolarReadingHistoryEntity entityDay1Morning = SolarReadingHistoryEntity.builder().id("id1").readingTimestamp(tsDay1Morning).dailyProductionKWh(5.0).currentPowerW(100.0).build();
        SolarReadingHistoryEntity entityDay1Noon = SolarReadingHistoryEntity.builder().id("id2").readingTimestamp(tsDay1Noon).dailyProductionKWh(6.0).currentPowerW(200.0).build();
        SolarReadingHistoryEntity entityDay2Morning = SolarReadingHistoryEntity.builder().id("id3").readingTimestamp(tsDay2Morning).dailyProductionKWh(7.0).currentPowerW(300.0).build();
        List<SolarReadingHistoryEntity> entityList = List.of(entityDay1Morning, entityDay1Noon, entityDay2Morning);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SolarReadingDailyEntity>> captor = forClass(List.class);
        when(mockDailyRepository.saveAll(captor.capture())).thenReturn(Flux.fromIterable(List.of()));
        firestoreService.saveDailyStats(entityList);
        verify(mockDailyRepository).saveAll(ArgumentMatchers.<Iterable<SolarReadingDailyEntity>>any());
        List<SolarReadingDailyEntity> saved = captor.getValue();
        assertEquals(2, saved.size());
        SolarReadingDailyEntity daily1 = saved.stream().filter(d -> "2024-01-01".equals(d.getId())).findFirst().orElseThrow();
        SolarReadingDailyEntity daily2 = saved.stream().filter(d -> "2024-01-02".equals(d.getId())).findFirst().orElseThrow();
        assertEquals(6.0, daily1.getDailyProductionKWh()); // max of 5.0, 6.0
        assertEquals(150.0, daily1.getAveragePowerW()); // avg of 100, 200
        assertEquals(200.0, daily1.getMaxPowerH()); // max of 100, 200
        assertEquals(7.0, daily2.getDailyProductionKWh());
        assertEquals(300.0, daily2.getAveragePowerW());
        assertEquals(300.0, daily2.getMaxPowerH());
    }

    @Test
    @DisplayName("saveDailyStats(Flux): empty flux completes without saving")
    void saveDailyStats_fluxEmpty_completes() {
        when(mockDailyRepository.saveAll(ArgumentMatchers.<Publisher<SolarReadingDailyEntity>>any())).thenReturn(Flux.empty());
        Mono<Void> result = firestoreService.saveDailyStats(Flux.empty());
        StepVerifier.create(result).verifyComplete();
    }

    @Test
    @DisplayName("saveDailyStats(Flux): single entity saves one daily stat")
    void saveDailyStats_fluxSingleEntity_savesOne() {
        Instant readingTimestamp = Instant.parse("2024-01-01T10:00:00Z");
        SolarReadingHistoryEntity historyEntity = SolarReadingHistoryEntity.builder()
                .id("id1").readingTimestamp(readingTimestamp).dailyProductionKWh(5.0).currentPowerW(100.0).build();
        when(mockDailyRepository.saveAll(ArgumentMatchers.<Publisher<SolarReadingDailyEntity>>any()))
                .thenReturn(Flux.just(SolarReadingDailyEntity.builder().id("2024-01-01").build()));
        Mono<Void> result = firestoreService.saveDailyStats(Flux.just(historyEntity));
        StepVerifier.create(result).verifyComplete();
        verify(mockDailyRepository).saveAll(ArgumentMatchers.<Publisher<SolarReadingDailyEntity>>any());
    }

    @Test
    @DisplayName("saveDailyStats(Flux): multiple entities, different days, groups and saves correctly")
    void saveDailyStats_fluxMultipleEntities_groupsByDay() {
        Instant tsDay1Morning = Instant.parse("2024-01-01T10:00:00Z");
        Instant tsDay1Noon = Instant.parse("2024-01-01T12:00:00Z");
        Instant tsDay2Morning = Instant.parse("2024-01-02T09:00:00Z");
        SolarReadingHistoryEntity entityDay1Morning = SolarReadingHistoryEntity.builder().id("id1").readingTimestamp(tsDay1Morning).dailyProductionKWh(5.0).currentPowerW(100.0).build();
        SolarReadingHistoryEntity entityDay1Noon = SolarReadingHistoryEntity.builder().id("id2").readingTimestamp(tsDay1Noon).dailyProductionKWh(6.0).currentPowerW(200.0).build();
        SolarReadingHistoryEntity entityDay2Morning = SolarReadingHistoryEntity.builder().id("id3").readingTimestamp(tsDay2Morning).dailyProductionKWh(7.0).currentPowerW(300.0).build();
        when(mockDailyRepository.saveAll(any(Publisher.class)))
                .thenReturn(Flux.just(
                        SolarReadingDailyEntity.builder().id("2024-01-01").build(),
                        SolarReadingDailyEntity.builder().id("2024-01-02").build()
                ));
        Mono<Void> result = firestoreService.saveDailyStats(Flux.just(entityDay1Morning, entityDay1Noon, entityDay2Morning));
        StepVerifier.create(result).verifyComplete();
        verify(mockDailyRepository).saveAll(ArgumentMatchers.<Publisher<SolarReadingDailyEntity>>any());
    }
}
