//package dev.devanks.solarman.archiver.repository;
//
//import dev.devanks.solarman.archiver.entity.SolarReadingHistoryEntity;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.ActiveProfiles;
//import reactor.core.publisher.Flux;
//import reactor.test.StepVerifier;
//
//import java.time.Instant;
//import java.time.temporal.ChronoUnit;
//import java.util.Arrays;
//import java.util.List;
//import java.util.UUID;
//
//@SpringBootTest // Loads the Spring context, including your repository
//@ActiveProfiles("test,local") // Activates application-test.properties
//@TestInstance(TestInstance.Lifecycle.PER_CLASS) // To allow non-static @BeforeAll with Testcontainers
//@DisplayName("SolarReadingHistoryRepository Integration Test with Emulator")
//@Slf4j
//class SolarReadingHistoryRepositoryIntegrationTest {
//
//    // --- End of Testcontainers Setup ---
//
//    @Autowired
//    private SolarReadingHistoryRepository repository; // Autowire the repository under test
//
//    @BeforeEach
//    void setUp() {
//        // Clear data before each test to ensure test isolation
//        // This is a bit tricky with Firestore emulator without admin SDK or specific clear commands.
//        // A common approach is to delete all documents from the collection.
//        // For simplicity in this example, we'll rely on unique IDs for each test run
//        // or manually ensure the emulator is fresh if not using Testcontainers to restart it.
//        // If using Testcontainers, it starts fresh for each class run (or can be configured).
//
//        // A more robust cleanup:
//        repository.deleteAll()
//                .block(); // Block to ensure cleanup before test runs
//    }
//
//    private SolarReadingHistoryEntity createEntity(String id, Instant readingTimestamp) {
//        return SolarReadingHistoryEntity.builder()
//                .id(id)
//                .readingTimestamp(readingTimestamp)
//                .ingestedTimestamp(Instant.now())
//                .isOnline(true)
//                .currentPowerW(100.0)
//                .dailyProductionKWh(1.0)
//                .build();
//    }
//
//    @Test
//    @DisplayName("findByReadingTimestampBefore returns only older entities")
//    void findByReadingTimestampBefore_returnsOnlyOlderEntities() {
//        Instant now = Instant.now();
//        Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);
//        Instant twoHoursAgo = now.minus(2, ChronoUnit.HOURS);
//        Instant halfHourAgo = now.minus(30, ChronoUnit.MINUTES);
//
//        SolarReadingHistoryEntity entityOld1 = createEntity(UUID.randomUUID().toString(), twoHoursAgo);
//        SolarReadingHistoryEntity entityOld2 = createEntity(UUID.randomUUID().toString(), now.minus(90, ChronoUnit.MINUTES)); // 1.5 hours ago
//        SolarReadingHistoryEntity entityNew1 = createEntity(UUID.randomUUID().toString(), halfHourAgo);
//        SolarReadingHistoryEntity entityRecent = createEntity(UUID.randomUUID().toString(), now.minus(10, ChronoUnit.MINUTES));
//
//        List<SolarReadingHistoryEntity> allEntities = Arrays.asList(entityOld1, entityOld2, entityNew1, entityRecent);
//
//        // Save all entities to the emulator
//        repository.saveAll(allEntities).blockLast(); // blockLast to ensure save completes before querying
//
//        // Define the cutoff time
//        Instant cutoffTimestamp = oneHourAgo; // Entities older than one hour ago
//
//        // Act
//        Flux<SolarReadingHistoryEntity> result = repository.findByReadingTimestampBefore(cutoffTimestamp);
//
//        // Assert
//        StepVerifier.create(result)
//                .expectNextMatches(entity -> entity.getId().equals(entityOld1.getId()) || entity.getId().equals(entityOld2.getId()))
//                .expectNextMatches(entity -> entity.getId().equals(entityOld1.getId()) || entity.getId().equals(entityOld2.getId()))
//                .verifyComplete();
//
//        // Verify counts if order is not guaranteed by the client-side filter from findAll
//        StepVerifier.create(result.collectList())
//                .assertNext(list -> {
//                    Assertions.assertEquals(2, list.size(), "Should find 2 older entities");
//                    Assertions.assertTrue(list.stream().anyMatch(e -> e.getId().equals(entityOld1.getId())), "entityOld1 should be present");
//                    Assertions.assertTrue(list.stream().anyMatch(e -> e.getId().equals(entityOld2.getId())), "entityOld2 should be present");
//                    Assertions.assertFalse(list.stream().anyMatch(e -> e.getId().equals(entityNew1.getId())), "entityNew1 should not be present");
//                    Assertions.assertFalse(list.stream().anyMatch(e -> e.getId().equals(entityRecent.getId())), "entityRecent should not be present");
//                })
//                .verifyComplete();
//    }
//
//    @Test
//    @DisplayName("findByReadingTimestampBefore returns empty Flux if no entities are older")
//    void findByReadingTimestampBefore_returnsEmptyIfNoneOlder() {
//        Instant now = Instant.now();
//        SolarReadingHistoryEntity entityNew1 = createEntity(UUID.randomUUID().toString(), now.minus(10, ChronoUnit.MINUTES));
//        SolarReadingHistoryEntity entityNew2 = createEntity(UUID.randomUUID().toString(), now.minus(20, ChronoUnit.MINUTES));
//
//        repository.saveAll(Arrays.asList(entityNew1, entityNew2)).blockLast();
//
//        Instant cutoffTimestamp = now.minus(30, ChronoUnit.MINUTES); // Cutoff is older than all entities
//
//        // Act
//        Flux<SolarReadingHistoryEntity> result = repository.findByReadingTimestampBefore(cutoffTimestamp);
//
//        // Assert
//        StepVerifier.create(result)
//                .expectNextCount(0)
//                .verifyComplete();
//    }
//
//    @Test
//    @DisplayName("findByReadingTimestampBefore returns all entities if cutoff is in the future")
//    void findByReadingTimestampBefore_returnsAllIfCutoffInFuture() {
//        Instant now = Instant.now();
//        SolarReadingHistoryEntity entity1 = createEntity(UUID.randomUUID().toString(), now.minus(1, ChronoUnit.HOURS));
//        SolarReadingHistoryEntity entity2 = createEntity(UUID.randomUUID().toString(), now.minus(2, ChronoUnit.HOURS));
//
//        repository.saveAll(Arrays.asList(entity1, entity2)).blockLast();
//
//        Instant cutoffTimestamp = now.plus(1, ChronoUnit.HOURS); // Cutoff is in the future
//
//        // Act
//        Flux<SolarReadingHistoryEntity> result = repository.findByReadingTimestampBefore(cutoffTimestamp);
//
//        // Assert
//        StepVerifier.create(result.collectList())
//                .assertNext(list -> {
//                    Assertions.assertEquals(2, list.size());
//                    Assertions.assertTrue(list.stream().anyMatch(e -> e.getId().equals(entity1.getId())));
//                    Assertions.assertTrue(list.stream().anyMatch(e -> e.getId().equals(entity2.getId())));
//                })
//                .verifyComplete();
//    }
//}
