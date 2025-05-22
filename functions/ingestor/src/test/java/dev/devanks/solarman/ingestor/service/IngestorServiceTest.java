// functions/ingestor/src/test/java/dev/devanks/solarman/ingestor/service/IngestorServiceTest.java
package dev.devanks.solarman.ingestor.service;

// import com.google.api.core.ApiFuture; // REMOVE
// import com.google.cloud.firestore.*; // REMOVE native client imports

import dev.devanks.solarman.ingestor.client.SolarmanApiClient;
import dev.devanks.solarman.ingestor.config.IngestorProperties;
import dev.devanks.solarman.ingestor.entity.LatestSolarReadingEntity;
import dev.devanks.solarman.ingestor.entity.SolarReadingHistoryEntity;
import dev.devanks.solarman.ingestor.exception.IngestionException;
import dev.devanks.solarman.ingestor.model.SolarmanAPIResponse;
import dev.devanks.solarman.ingestor.repository.LatestSolarReadingRepository;
import dev.devanks.solarman.ingestor.repository.SolarReadingHistoryRepository;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestorServiceTest {

    // Mocks for dependencies
    @Mock
    private IngestorProperties mockProperties;
    @Mock
    private IngestorProperties.GcpProperties mockGcpProperties;
    @Mock
    private SolarmanApiClient mockSolarmanApiClient;
    @Mock
    private LatestSolarReadingRepository mockLatestReadingRepository; // <<< ADD Repository Mock
    @Mock
    private SolarReadingHistoryRepository mockHistoryRepository;     // <<< ADD Repository Mock

    // Argument Captors for Entities
    @Captor
    private ArgumentCaptor<LatestSolarReadingEntity> latestEntityCaptor;
    @Captor
    private ArgumentCaptor<SolarReadingHistoryEntity> historyEntityCaptor;

    // Service under test
    @InjectMocks
    private IngestorService ingestorService;

    // Constants
    private static final String LATEST_DOC_ID = "fixed-latest-id"; // Fixed ID for latest doc
    private static final long API_TIMESTAMP = 1678886400L;

    @Test
    @DisplayName("performIngestion - Success Path")
    void performIngestion_Success() { // Keep throws Exception for safety? .block() can throw
        // Arrange

        // Link nested mock properties
        when(mockProperties.getGcp()).thenReturn(mockGcpProperties);
        // Mock the fixed ID needed for the latest entity
        when(mockGcpProperties.getFirestoreLatestDocumentId()).thenReturn(LATEST_DOC_ID);
        // --- Mock API Response via Feign Client ---
        SolarmanAPIResponse apiResponse = SolarmanAPIResponse.builder()
                .generationValue(15.5).generationPower(2500.0).networkStatus("NORMAL").lastUpdateTime(API_TIMESTAMP).build();
        when(mockSolarmanApiClient.getSystemData()).thenReturn(apiResponse);

        // --- Mock Repository Save Calls ---
        // Use thenAnswer to return the input entity, simulating save completion
        when(mockLatestReadingRepository.save(latestEntityCaptor.capture()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, LatestSolarReadingEntity.class)));
        when(mockHistoryRepository.save(historyEntityCaptor.capture()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, SolarReadingHistoryEntity.class)));


        // Act
        String result = ingestorService.performIngestion();

        // Assert
        assertThat(result).startsWith("Ingestion successful (separate saves) in");

        // --- Verify Interactions ---
        verify(mockSolarmanApiClient).getSystemData();
        verify(mockLatestReadingRepository).save(any(LatestSolarReadingEntity.class));
        verify(mockHistoryRepository).save(any(SolarReadingHistoryEntity.class));

        // --- Verify Entities Saved ---
        LatestSolarReadingEntity capturedLatest = latestEntityCaptor.getValue();
        assertThat(capturedLatest.getId()).isEqualTo(LATEST_DOC_ID); // Verify fixed ID
        assertThat(capturedLatest.getDailyProductionKWh()).isEqualTo(15.5);
        assertThat(capturedLatest.getCurrentPowerW()).isEqualTo(2500.0);
        assertThat(capturedLatest.isOnline()).isTrue();
        assertThat(capturedLatest.getReadingTimestamp()).isEqualTo(Instant.ofEpochSecond(API_TIMESTAMP));
        assertThat(capturedLatest.getIngestedTimestamp()).isNotNull().isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));

        SolarReadingHistoryEntity capturedHistory = historyEntityCaptor.getValue();
        assertThat(capturedHistory.getId()).isNotNull().startsWith(String.valueOf(Instant.ofEpochSecond(API_TIMESTAMP).toEpochMilli())); // Verify dynamic ID format
        assertThat(capturedHistory.getDailyProductionKWh()).isEqualTo(15.5);
        // ... other history fields ...
    }

    @Test
    @DisplayName("performIngestion - Failure on API Call (Feign Exception)")
    void performIngestion_ApiCallFailure() {
        // Arrange
        Request dummyRequest = Request.create(Request.HttpMethod.GET, "/fake", Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
        FeignException feignException = new FeignException.ServiceUnavailable("Service down", dummyRequest, null, Collections.emptyMap());
        when(mockSolarmanApiClient.getSystemData()).thenThrow(feignException);

        // Act & Assert
        assertThatThrownBy(() -> ingestorService.performIngestion())
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("Ingestion failed: Error calling Solarman API")
                .hasCauseInstanceOf(FeignException.class);

        // Verify no repository calls were made
        verify(mockLatestReadingRepository, never()).save(any());
        verify(mockHistoryRepository, never()).save(any());
    }


    @Test
    @DisplayName("performIngestion - Failure on Latest Repo Save")
    void performIngestion_LatestRepoFailure() {
        // Link nested mock properties
        when(mockProperties.getGcp()).thenReturn(mockGcpProperties);
        // Mock the fixed ID needed for the latest entity
        when(mockGcpProperties.getFirestoreLatestDocumentId()).thenReturn(LATEST_DOC_ID);
        // Arrange
        SolarmanAPIResponse apiResponse = SolarmanAPIResponse.builder()
                .generationValue(10.0).generationPower(1000.0).networkStatus("NORMAL").lastUpdateTime(API_TIMESTAMP).build();
        when(mockSolarmanApiClient.getSystemData()).thenReturn(apiResponse);

        // Simulate error on latest save
        RuntimeException dbException = new RuntimeException("Firestore connection failed");
        when(mockLatestReadingRepository.save(any(LatestSolarReadingEntity.class)))
                .thenReturn(Mono.error(dbException));
        // Don't need to mock history save as it won't be reached if using then/flatMap chaining correctly

        // Act & Assert
        assertThatThrownBy(() -> ingestorService.performIngestion())
                .isInstanceOf(IngestionException.class) // Service wraps the exception
                .hasMessageContaining("Ingestion failed: Firestore connection failed") // Check wrapped message
                .hasCauseInstanceOf(RuntimeException.class); // Verify original cause from reactive chain

        // Verify latest save was attempted, history save was not
        verify(mockLatestReadingRepository).save(any(LatestSolarReadingEntity.class));
        verify(mockHistoryRepository, never()).save(any(SolarReadingHistoryEntity.class));
    }

    @Test
    @DisplayName("performIngestion - Failure on History Repo Save")
    void performIngestion_HistoryRepoFailure() {
        // Link nested mock properties
        when(mockProperties.getGcp()).thenReturn(mockGcpProperties);
        // Mock the fixed ID needed for the latest entity
        when(mockGcpProperties.getFirestoreLatestDocumentId()).thenReturn(LATEST_DOC_ID);
        // Arrange
        SolarmanAPIResponse apiResponse = SolarmanAPIResponse.builder()
                .generationValue(10.0).generationPower(1000.0).networkStatus("NORMAL").lastUpdateTime(API_TIMESTAMP).build();
        when(mockSolarmanApiClient.getSystemData()).thenReturn(apiResponse);

        // Simulate success on latest save
        when(mockLatestReadingRepository.save(any(LatestSolarReadingEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, LatestSolarReadingEntity.class)));

        // Simulate error on history save
        RuntimeException dbException = new RuntimeException("History write quota exceeded");
        when(mockHistoryRepository.save(any(SolarReadingHistoryEntity.class)))
                .thenReturn(Mono.error(dbException));

        // Act & Assert
        assertThatThrownBy(() -> ingestorService.performIngestion())
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("Ingestion failed: History write quota exceeded")
                .hasCauseInstanceOf(RuntimeException.class);

        // Verify both saves were attempted
        verify(mockLatestReadingRepository).save(any(LatestSolarReadingEntity.class));
        verify(mockHistoryRepository).save(any(SolarReadingHistoryEntity.class));
    }


    @Test
    @DisplayName("Entity Mapping - Handles Missing API Data")
    void entityMapping_HandlesMissingData() {
        // Link nested mock properties
        when(mockProperties.getGcp()).thenReturn(mockGcpProperties);
        // Mock the fixed ID needed for the latest entity
        when(mockGcpProperties.getFirestoreLatestDocumentId()).thenReturn(LATEST_DOC_ID);
        // Arrange
        SolarmanAPIResponse incompleteResponse = SolarmanAPIResponse.builder().build(); // All nulls
        when(mockSolarmanApiClient.getSystemData()).thenReturn(incompleteResponse);

        when(mockLatestReadingRepository.save(latestEntityCaptor.capture()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, LatestSolarReadingEntity.class)));
        when(mockHistoryRepository.save(historyEntityCaptor.capture()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, SolarReadingHistoryEntity.class)));

        // Act
        ingestorService.performIngestion();

        // Assert - Verify captured entities have default values
        LatestSolarReadingEntity capturedLatest = latestEntityCaptor.getValue();
        assertThat(capturedLatest.getId()).isEqualTo(LATEST_DOC_ID);
        assertThat(capturedLatest.getDailyProductionKWh()).isEqualTo(0.0);
        assertThat(capturedLatest.getCurrentPowerW()).isEqualTo(0.0);
        assertThat(capturedLatest.isOnline()).isFalse(); // null status maps to false
        assertThat(capturedLatest.getReadingTimestamp()).isNotNull().isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS)); // Defaults to now
        assertThat(capturedLatest.getIngestedTimestamp()).isNotNull().isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));

        SolarReadingHistoryEntity capturedHistory = historyEntityCaptor.getValue();
        assertThat(capturedHistory.getId()).isNotNull(); // Check ID is generated
        assertThat(capturedHistory.getDailyProductionKWh()).isEqualTo(0.0);
        // ... other fields ...
    }
}
