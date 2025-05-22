package dev.devanks.solarman.archiver.repository;

import dev.devanks.solarman.archiver.entity.SolarReadingDailyEntity;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SolarReadingDailyRepositoryTest {

    @Autowired
    private SolarReadingDailyRepository repository;

    private static final DateTimeFormatter ID_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Helper to create a consistent start-of-day Instant in UTC
    private Instant getStartOfDayUtc(LocalDate localDate) {
        return localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll().block();
    }

    @Test
    void shouldSaveNewEntityWithAppProvidedDateStringId() {
        LocalDate today = LocalDate.now();
        String id = today.format(ID_FORMATTER);
        Instant dateInstant = getStartOfDayUtc(today);

        SolarReadingDailyEntity entity = SolarReadingDailyEntity.builder()
                .id(id) // App-provided "YYYY-MM-DD" ID
                .date(dateInstant)
                .dailyProductionKWh(10.5)
                .averagePowerW(500.0)
                .maxPowerH(1200.0)
                .build();

        StepVerifier.create(repository.save(entity))
                .assertNext(savedEntity -> {
                    assertNotNull(savedEntity);
                    assertEquals(id, savedEntity.getId());
                    assertEquals(dateInstant, savedEntity.getDate());
                    assertEquals(10.5, savedEntity.getDailyProductionKWh());
                })
                .verifyComplete();

        // Verify it's actually in the database by the app-provided ID
        StepVerifier.create(repository.findById(id))
                .expectNextMatches(found -> found.getId().equals(id) && found.getDailyProductionKWh() == 10.5)
                .verifyComplete();
    }

    @Test
    void shouldNotSaveNewEntityAndThrowError_whenIdIsNull() {
        // This test verifies that if `id` is null, Firestore auto-generates one.
        // This might not be the primary use case if you always provide "YYYY-MM-DD" IDs,
        // but it's good to confirm default behavior.
        LocalDate today = LocalDate.now();
        Instant dateInstant = getStartOfDayUtc(today);

        Assert.assertThrows(NullPointerException.class, () -> SolarReadingDailyEntity.builder()
                .id(null) // ID is null, Firestore should generate it
                .date(dateInstant)
                .dailyProductionKWh(12.3)
                .averagePowerW(550.0)
                .maxPowerH(1300.0)
                .build());
    }


    @Test
    void shouldUpdateExistingEntityWithAppProvidedId() {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        String id = targetDate.format(ID_FORMATTER);
        Instant dateInstant = getStartOfDayUtc(targetDate);

        SolarReadingDailyEntity initialEntity = SolarReadingDailyEntity.builder()
                .id(id)
                .date(dateInstant)
                .dailyProductionKWh(15.0)
                .averagePowerW(600.0)
                .maxPowerH(1400.0)
                .build();

        repository.save(initialEntity).block(); // Save initial entity

        SolarReadingDailyEntity updatedEntity = SolarReadingDailyEntity.builder()
                .id(id) // Same ID
                .date(dateInstant) // Date field usually remains the same for this ID
                .dailyProductionKWh(16.5) // Updated value
                .averagePowerW(650.0)     // Updated value
                .maxPowerH(1450.0)
                .build();

        StepVerifier.create(repository.save(updatedEntity))
                .assertNext(savedEntity -> {
                    assertEquals(id, savedEntity.getId());
                    assertEquals(16.5, savedEntity.getDailyProductionKWh());
                    assertEquals(650.0, savedEntity.getAveragePowerW());
                })
                .verifyComplete();

        StepVerifier.create(repository.findById(id))
                .assertNext(foundEntity -> assertEquals(16.5, foundEntity.getDailyProductionKWh()))
                .verifyComplete();
    }

    @Test
    void findById_whenEntityExists_shouldReturnEntity() {
        LocalDate targetDate = LocalDate.now().minusDays(5);
        String id = targetDate.format(ID_FORMATTER);
        Instant dateInstant = getStartOfDayUtc(targetDate);

        SolarReadingDailyEntity entity = SolarReadingDailyEntity.builder()
                .id(id)
                .date(dateInstant)
                .dailyProductionKWh(20.0)
                .build();
        repository.save(entity).block();

        StepVerifier.create(repository.findById(id))
                .assertNext(foundEntity -> {
                    assertNotNull(foundEntity);
                    assertEquals(id, foundEntity.getId());
                    assertEquals(dateInstant, foundEntity.getDate());
                    assertEquals(20.0, foundEntity.getDailyProductionKWh());
                })
                .verifyComplete();
    }

    @Test
    void findById_whenEntityDoesNotExist_shouldReturnEmpty() {
        String nonExistentId = "1900-01-01";
        StepVerifier.create(repository.findById(nonExistentId))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void findAll_whenNoEntities_shouldReturnEmptyFlux() {
        StepVerifier.create(repository.findAll())
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void findAll_whenEntitiesExist_shouldReturnAllEntities() {
        LocalDate date1 = LocalDate.now().minusDays(2);
        String id1 = date1.format(ID_FORMATTER);
        Instant instant1 = getStartOfDayUtc(date1);

        LocalDate date2 = LocalDate.now().minusDays(1);
        String id2 = date2.format(ID_FORMATTER);
        Instant instant2 = getStartOfDayUtc(date2);

        SolarReadingDailyEntity entity1 = SolarReadingDailyEntity.builder().id(id1).date(instant1).dailyProductionKWh(5.0).build();
        SolarReadingDailyEntity entity2 = SolarReadingDailyEntity.builder().id(id2).date(instant2).dailyProductionKWh(6.0).build();

        repository.saveAll(Arrays.asList(entity1, entity2)).blockLast();

        StepVerifier.create(repository.findAll().collectList())
                .assertNext(entities -> {
                    assertEquals(2, entities.size());
                    assertTrue(entities.stream().anyMatch(e -> e.getId().equals(id1) && e.getDailyProductionKWh() == 5.0));
                    assertTrue(entities.stream().anyMatch(e -> e.getId().equals(id2) && e.getDailyProductionKWh() == 6.0));
                })
                .verifyComplete();
    }

    @Test
    void deleteById_shouldRemoveEntity() {
        LocalDate targetDate = LocalDate.now().minusDays(10);
        String id = targetDate.format(ID_FORMATTER);
        Instant dateInstant = getStartOfDayUtc(targetDate);

        SolarReadingDailyEntity entity = SolarReadingDailyEntity.builder().id(id).date(dateInstant).dailyProductionKWh(25.0).build();
        repository.save(entity).block();

        StepVerifier.create(repository.findById(id)).expectNextCount(1).verifyComplete();

        StepVerifier.create(repository.deleteById(id)).verifyComplete();

        StepVerifier.create(repository.findById(id)).expectNextCount(0).verifyComplete();
    }

    @Test
    void deleteAll_shouldRemoveAllEntities() {
        LocalDate date1 = LocalDate.now().minusDays(3);
        String id1 = date1.format(ID_FORMATTER);
        Instant instant1 = getStartOfDayUtc(date1);

        LocalDate date2 = LocalDate.now().minusDays(4);
        String id2 = date2.format(ID_FORMATTER);
        Instant instant2 = getStartOfDayUtc(date2);

        SolarReadingDailyEntity entity1 = SolarReadingDailyEntity.builder().id(id1).date(instant1).dailyProductionKWh(1.0).build(); // Added some value to make entities distinct
        SolarReadingDailyEntity entity2 = SolarReadingDailyEntity.builder().id(id2).date(instant2).dailyProductionKWh(2.0).build();
        repository.saveAll(List.of(entity1, entity2)).blockLast();

        // Verify count before delete
        StepVerifier.create(repository.count())
                .expectNext(2L)
                .verifyComplete();

        repository.deleteAll().block();

        // Verify count after delete
        StepVerifier.create(repository.count())
                .expectNext(0L)
                .verifyComplete();
    }

}
