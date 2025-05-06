// functions/ingestor/src/test/java/dev/devanks/solarman/ingestor/service/IngestorServiceTest.java
package dev.devanks.solarman.ingestor.service;

import dev.devanks.solarman.ingestor.client.SolarmanApiClient;
import dev.devanks.solarman.ingestor.entity.LatestSolarReadingEntity;
import dev.devanks.solarman.ingestor.entity.SolarReadingHistoryEntity;
import dev.devanks.solarman.ingestor.mapper.HistoryReadingMapper;
import dev.devanks.solarman.ingestor.mapper.LatestReadingMapper;
import dev.devanks.solarman.ingestor.model.IngestionResult;
import dev.devanks.solarman.ingestor.model.SolarmanAPIResponse;
import dev.devanks.solarman.ingestor.repository.LatestSolarReadingRepository;
import dev.devanks.solarman.ingestor.repository.SolarReadingHistoryRepository;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestorServiceTest {

    // Mocks for dependencies
    @Mock private SolarmanApiClient mockSolarmanApiClient;
    @Mock private LatestSolarReadingRepository mockLatestReadingRepository;
    @Mock private SolarReadingHistoryRepository mockHistoryRepository;
    @Mock private LatestReadingMapper mockLatestMapper; // <<< Mock Mapper
    @Mock private HistoryReadingMapper mockHistoryMapper; // <<< Mock Mapper

    // Service under test
    @InjectMocks
    private IngestorService ingestorService;

    // Test Data
    private SolarmanAPIResponse dummyApiResponse;
    private LatestSolarReadingEntity dummyLatestEntity;
    private SolarReadingHistoryEntity dummyHistoryEntity;
    private static final String LATEST_DOC_ID = "fixed-latest-id";
    private static final String HISTORY_DOC_ID = "1678886400000000000"; // Example generated ID

    @BeforeEach
    void setUp() {
        dummyApiResponse = SolarmanAPIResponse.builder().generationValue(1.0).build(); // Minimal needed
        dummyLatestEntity = LatestSolarReadingEntity.builder().id(LATEST_DOC_ID).dailyProductionKWh(1.0).build();
        dummyHistoryEntity = SolarReadingHistoryEntity.builder().id(HISTORY_DOC_ID).dailyProductionKWh(1.0).build();
    }

    @Test
    @DisplayName("performIngestion - Success Path - Returns Success POJO")
    void performIngestion_Success_ReturnsPojo() {
        // Arrange
        when(mockSolarmanApiClient.getSystemData()).thenReturn(dummyApiResponse);
        // Mock mapper calls
        when(mockLatestMapper.mapToEntity(eq(dummyApiResponse), any(Instant.class))).thenReturn(dummyLatestEntity);
        when(mockHistoryMapper.mapToEntity(eq(dummyApiResponse), any(Instant.class))).thenReturn(dummyHistoryEntity);
        // Mock repository saves
        when(mockLatestReadingRepository.save(dummyLatestEntity)).thenReturn(Mono.just(dummyLatestEntity));
        when(mockHistoryRepository.save(dummyHistoryEntity)).thenReturn(Mono.just(dummyHistoryEntity));

        // Act
        IngestionResult result = ingestorService.performIngestion();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(IngestionResult.Status.SUCCESS);
        assertThat(result.getMessage()).startsWith("Ingestion successful (mapped & saved)");
        assertThat(result.getLatestDocumentId()).isEqualTo(LATEST_DOC_ID);
        assertThat(result.getHistoryDocumentId()).isEqualTo(HISTORY_DOC_ID);
        assertThat(result.getDurationMs()).isNotNull().isPositive();
        assertThat(result.getErrorDetails()).isNull();

        // Verify Interactions
        verify(mockSolarmanApiClient).getSystemData();
        verify(mockLatestMapper).mapToEntity(eq(dummyApiResponse), any(Instant.class));
        verify(mockHistoryMapper).mapToEntity(eq(dummyApiResponse), any(Instant.class));
        verify(mockLatestReadingRepository).save(dummyLatestEntity);
        verify(mockHistoryRepository).save(dummyHistoryEntity);
    }

    @Test
    @DisplayName("performIngestion - Failure on API Call - Returns Failure POJO")
    void performIngestion_ApiCallFailure_ReturnsPojo() {
        // Arrange
        Request dummyRequest = Request.create(Request.HttpMethod.GET, "/fake", Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
        FeignException feignException = new FeignException.ServiceUnavailable("Service down", dummyRequest, null, Collections.emptyMap());
        when(mockSolarmanApiClient.getSystemData()).thenThrow(feignException);

        // Act
        IngestionResult result = ingestorService.performIngestion(); // No longer throws, returns POJO

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(IngestionResult.Status.FAILURE);
        assertThat(result.getMessage()).isEqualTo("Ingestion failed: Solarman API call failed (Feign)"); // Check message
        assertThat(result.getErrorDetails()).contains("java.lang.RuntimeException: Solarman API call failed (Feign)"); // Check details
        assertThat(result.getLatestDocumentId()).isNull();
        assertThat(result.getHistoryDocumentId()).isNull();
        assertThat(result.getDurationMs()).isNull(); // Duration might not be relevant on failure

        // Verify no mapper or repository calls
        verify(mockLatestMapper, never()).mapToEntity(any(), any());
        verify(mockHistoryMapper, never()).mapToEntity(any(), any());
        verify(mockLatestReadingRepository, never()).save(any());
        verify(mockHistoryRepository, never()).save(any());
    }


    @Test
    @DisplayName("performIngestion - Failure on Latest Repo Save - Returns Failure POJO")
    void performIngestion_LatestRepoFailure_ReturnsPojo() {
        // Arrange
        when(mockSolarmanApiClient.getSystemData()).thenReturn(dummyApiResponse);
        when(mockLatestMapper.mapToEntity(eq(dummyApiResponse), any(Instant.class))).thenReturn(dummyLatestEntity);
        when(mockHistoryMapper.mapToEntity(eq(dummyApiResponse), any(Instant.class))).thenReturn(dummyHistoryEntity); // Map still happens

        RuntimeException dbException = new RuntimeException("Firestore connection failed");
        when(mockLatestReadingRepository.save(dummyLatestEntity)).thenReturn(Mono.error(dbException));
        // Don't mock history save, it shouldn't be called

        // Act
        IngestionResult result = ingestorService.performIngestion();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(IngestionResult.Status.FAILURE);
        assertThat(result.getMessage()).isEqualTo("Ingestion failed: Firestore connection failed");
        assertThat(result.getErrorDetails()).contains("java.lang.RuntimeException: Firestore connection failed");
        assertThat(result.getLatestDocumentId()).isNull(); // IDs might not be relevant on failure
        assertThat(result.getHistoryDocumentId()).isNull();
        assertThat(result.getDurationMs()).isNull();

        // Verify API, mappers, and latest repo save were called
        verify(mockSolarmanApiClient).getSystemData();
        verify(mockLatestMapper).mapToEntity(eq(dummyApiResponse), any(Instant.class));
        verify(mockHistoryMapper).mapToEntity(eq(dummyApiResponse), any(Instant.class));
        verify(mockLatestReadingRepository).save(dummyLatestEntity);
        verify(mockHistoryRepository, never()).save(any()); // History save should not be attempted
    }

    // Add similar test for history repo failure if needed
}
