package dev.devanks.solarman.archiver.service;

import dev.devanks.solarman.archiver.config.ArchiveProperties;
import dev.devanks.solarman.archiver.entity.SolarReadingHistoryEntity;
import dev.devanks.solarman.archiver.service.io.GcsResourceProvider;
import dev.devanks.solarman.archiver.service.io.GcsWritableResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StorageWriterService Unit Tests (CSV Output - Always with Header)")
class StorageWriterServiceTest {

    @Mock
    private ArchiveProperties mockArchiveProperties;

    @Mock
    private GcsResourceProvider mockResourceProvider;

    @Mock
    private GcsWritableResource mockWritableResource;

    @InjectMocks
    private StorageWriterService storageWriterService;

    private static final String BUCKET_NAME = "test-bucket";
    private static final LocalDate TEST_ARCHIVE_DATE = LocalDate.of(2023, 5, 17);
    private static final String EXPECTED_CSV_HEADER = "id,readingTimestamp,ingestedTimestamp,isOnline,currentPowerW,dailyProductionKWh";

    @Test
    @DisplayName("archiveEntitiesToGCS: Happy path, writes entities correctly as CSV with header")
    void archiveEntitiesToGCS_happyPath_writesCsvWithHeader() throws IOException {
        // Arrange
        var entities = createMockEntities(2);
        var baos = new ByteArrayOutputStream();
        var expectedPathRegex = "gs://" + BUCKET_NAME + "/archive/2023/05/17/firestore-export-2023-05-17-.*\\.csv\\.gz";

        when(mockArchiveProperties.getBucketName()).thenReturn(BUCKET_NAME);
        when(mockResourceProvider.createWritableResource(matches(expectedPathRegex)))
                .thenReturn(mockWritableResource);
        when(mockWritableResource.getOutputStream()).thenReturn(baos);

        // Act
        var resultMono = storageWriterService.archiveEntitiesToGCS(entities, TEST_ARCHIVE_DATE); // No writeHeader flag

        // Assert
        StepVerifier.create(resultMono)
                .expectNextMatches(gcsPath -> {
                    assertThat(gcsPath).matches(expectedPathRegex);
                    try {
                        var bais = new ByteArrayInputStream(baos.toByteArray());
                        var gzipIs = new GZIPInputStream(bais);
                        var writtenContent = new String(gzipIs.readAllBytes(), StandardCharsets.UTF_8);
                        gzipIs.close();

                        var lines = writtenContent.trim().split("\n");
                        assertThat(lines).hasSize(3); // Header + 2 data lines
                        assertThat(lines[0]).isEqualTo(EXPECTED_CSV_HEADER);
                        assertThat(lines[1]).isEqualTo(entityToCsvRow(entities.get(0)));
                        assertThat(lines[2]).isEqualTo(entityToCsvRow(entities.get(1)));
                        return true;
                    } catch (IOException e) {
                        throw new RuntimeException("Error verifying GZIP CSV content", e);
                    }
                })
                .verifyComplete();

        verify(mockResourceProvider).createWritableResource(matches(expectedPathRegex));
        verify(mockWritableResource).getOutputStream();
    }

    @Test
    @DisplayName("archiveEntitiesToGCS: Returns empty Mono for empty entity list")
    void archiveEntitiesToGCS_emptyList_returnsEmptyMono() {
        StepVerifier.create(storageWriterService.archiveEntitiesToGCS(Collections.emptyList(), TEST_ARCHIVE_DATE))
                .verifyComplete();
        verifyNoInteractions(mockResourceProvider);
    }

    @Test
    @DisplayName("archiveEntitiesToGCS: Returns error Mono for null archiveForDate")
    void archiveEntitiesToGCS_nullDate_returnsErrorMono() {
        var entities = createMockEntities(1);
        StepVerifier.create(storageWriterService.archiveEntitiesToGCS(entities, null))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(mockResourceProvider);
    }

    @Test
    @DisplayName("archiveEntitiesToGCS: Handles IOException during getOutputStream")
    void archiveEntitiesToGCS_getOutputStreamFails() throws IOException {
        var entities = createMockEntities(1);
        var expectedPathRegex = "gs://" + BUCKET_NAME + "/archive/2023/05/17/firestore-export-2023-05-17-.*\\.csv\\.gz";

        when(mockArchiveProperties.getBucketName()).thenReturn(BUCKET_NAME);
        when(mockResourceProvider.createWritableResource(matches(expectedPathRegex)))
                .thenReturn(mockWritableResource);
        when(mockWritableResource.getOutputStream()).thenThrow(new IOException("Failed to get output stream!"));

        var resultMono = storageWriterService.archiveEntitiesToGCS(entities, TEST_ARCHIVE_DATE);

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().startsWith("GCS write failed for path gs://" + BUCKET_NAME) &&
                                throwable.getCause() instanceof IOException &&
                                throwable.getCause().getMessage().equals("Failed to get output stream!")
                )
                .verify();
    }

    @Test
    @DisplayName("archiveEntitiesToGCS: Handles IOException during CSV write")
    void archiveEntitiesToGCS_csvWriteFails() throws IOException {
        var entities = createMockEntities(1);
        var mockFailingUnderlyingStream = mock(OutputStream.class);
        var expectedPathRegex = "gs://" + BUCKET_NAME + "/archive/2023/05/17/firestore-export-2023-05-17-.*\\.csv\\.gz";

        when(mockArchiveProperties.getBucketName()).thenReturn(BUCKET_NAME);
        when(mockResourceProvider.createWritableResource(matches(expectedPathRegex)))
                .thenReturn(mockWritableResource);
        when(mockWritableResource.getOutputStream()).thenReturn(mockFailingUnderlyingStream);
        doThrow(new IOException("Simulated CSV disk full")).when(mockFailingUnderlyingStream).write(any(byte[].class));

        var resultMono = storageWriterService.archiveEntitiesToGCS(entities, TEST_ARCHIVE_DATE);

        StepVerifier.create(resultMono)
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().startsWith("GCS write failed for path gs://" + BUCKET_NAME) &&
                                throwable.getCause() instanceof IOException &&
                                throwable.getCause().getMessage().equals("Simulated CSV disk full")
                )
                .verify();
    }

    @Test
    @DisplayName("archiveEntitiesToGCS: Path generation is correct for CSV")
    void archiveEntitiesToGCS_pathGeneration() throws IOException {
        var entities = createMockEntities(1);
        var specificDate = LocalDate.of(2024, 1, 5);
        var expectedPathRegex = "gs://" + BUCKET_NAME + "/archive/2024/01/05/firestore-export-2024-01-05-\\d{6}-\\d{6}\\.csv\\.gz";

        when(mockArchiveProperties.getBucketName()).thenReturn(BUCKET_NAME);
        when(mockResourceProvider.createWritableResource(matches(expectedPathRegex)))
                .thenReturn(mockWritableResource);
        when(mockWritableResource.getOutputStream()).thenThrow(new IOException("Intentional fail for path test"));

        storageWriterService.archiveEntitiesToGCS(entities, specificDate)
                .as(StepVerifier::create)
                .expectError()
                .verify();

        verify(mockResourceProvider).createWritableResource(matches(expectedPathRegex));
    }

    @Test
    @DisplayName("archiveEntitiesToGCS: Returns empty Mono when entitiesToArchive is empty")
    void archiveEntitiesToGCS_nullEntities_returnsEmptyMono() {
        StepVerifier.create(storageWriterService.archiveEntitiesToGCS(null, TEST_ARCHIVE_DATE))
                .verifyComplete();
        verifyNoInteractions(mockResourceProvider);
    }

    private List<SolarReadingHistoryEntity> createMockEntities(int count) {
        var baseTimestamp = TEST_ARCHIVE_DATE.atStartOfDay(ZoneOffset.UTC).toInstant();
        return IntStream.range(0, count)
                .mapToObj(i -> SolarReadingHistoryEntity.builder()
                        .id("id" + i)
                        .dailyProductionKWh((double) i)
                        .currentPowerW((double) i * 100)
                        .isOnline(i % 2 == 0)
                        .readingTimestamp(baseTimestamp.plusSeconds(i * 3600L))
                        .ingestedTimestamp(baseTimestamp.plusSeconds(i * 3600L + 60))
                        .build())
                .toList();
    }

    private String entityToCsvRow(SolarReadingHistoryEntity entity) {
        return String.join(",",
                escapeCsvFieldInternal(entity.getId()),
                escapeCsvFieldInternal(entity.getReadingTimestamp() != null ? entity.getReadingTimestamp().toString() : ""),
                escapeCsvFieldInternal(entity.getIngestedTimestamp() != null ? entity.getIngestedTimestamp().toString() : ""),
                String.valueOf(entity.isOnline()),
                entity.getCurrentPowerW().toString(),
                entity.getDailyProductionKWh().toString()
        );
    }

    // Duplicating the escape logic for testing is okay, or make it public static if preferred
    private String escapeCsvFieldInternal(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\n") || field.contains("\"")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
