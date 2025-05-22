package dev.devanks.solarman.archiver.function;

import dev.devanks.solarman.archiver.service.ArchiveService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArchiveFunction Unit Tests")
class ArchiveFunctionTest {

    @Mock
    private ArchiveService mockArchiveService;

    @InjectMocks
    private ArchiveFunction archiveFunction;

    private static final String SUCCESS_SUMMARY = "Archival successful.";

    // --- Tests for the main Function bean: firestoreArchiver ---

    @Test
    @DisplayName("firestoreArchiver: with null payload, invokes default day archival via service")
    void firestoreArchiver_nullPayload_invokesDefaultDayArchival() {
        Function<HashMap<String, Object>, String> functionBean = archiveFunction.firestoreArchiver();
        when(mockArchiveService.performDefaultArchival()).thenReturn(Mono.just(SUCCESS_SUMMARY));

        String result = functionBean.apply(null);

        assertThat(result).isEqualTo(SUCCESS_SUMMARY);
        verify(mockArchiveService).performDefaultArchival();
    }

    @Test
    @DisplayName("firestoreArchiver: with empty payload, invokes default day archival via service")
    void firestoreArchiver_emptyPayload_invokesDefaultDayArchival() {
        Function<HashMap<String, Object>, String> functionBean = archiveFunction.firestoreArchiver();
        HashMap<String, Object> emptyPayload = new HashMap<>();
        when(mockArchiveService.performDefaultArchival()).thenReturn(Mono.just(SUCCESS_SUMMARY));

        String result = functionBean.apply(emptyPayload);

        assertThat(result).isEqualTo(SUCCESS_SUMMARY);
        verify(mockArchiveService).performDefaultArchival();
    }

    @Test
    @DisplayName("firestoreArchiver: with valid 'archiveBeforeDate', invokes range archival via service")
    void firestoreArchiver_validArchiveBeforeDate_invokesRangeArchival() {

        Function<HashMap<String, Object>, String> functionBean = archiveFunction.firestoreArchiver();

        LocalDate payloadExclusiveEndDate = LocalDate.of(2023, 10, 27);
        String dateStr = payloadExclusiveEndDate.format(ISO_LOCAL_DATE);
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("archiveBeforeDate", dateStr);

        when(mockArchiveService.archiveOlderThan(payloadExclusiveEndDate))
                .thenReturn(Mono.just(SUCCESS_SUMMARY));

        String result = functionBean.apply(payload);

        assertThat(result).isEqualTo(SUCCESS_SUMMARY);
        verify(mockArchiveService).archiveOlderThan(payloadExclusiveEndDate);
    }

    @Test
    @DisplayName("firestoreArchiver: with invalid 'archiveBeforeDate' format, returns error string")
    void firestoreArchiver_invalidArchiveBeforeDateFormat_returnsErrorString() {
        Function<HashMap<String, Object>, String> functionBean = archiveFunction.firestoreArchiver();
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("archiveBeforeDate", "invalid-date-format");

        String result = functionBean.apply(payload);

        assertThat(result).contains("Error: Invalid 'archiveBeforeDate' in payload");
        verifyNoInteractions(mockArchiveService);
    }

    @Test
    @DisplayName("firestoreArchiver: with 'archiveBeforeDate' not a string, returns error string")
    void firestoreArchiver_archiveBeforeDateNotString_returnsErrorString() {
        Function<HashMap<String, Object>, String> functionBean = archiveFunction.firestoreArchiver();
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("archiveBeforeDate", 12345); // Integer instead of String

        String result = functionBean.apply(payload);

        assertThat(result).contains("Error: Invalid 'archiveBeforeDate' in payload")
                .contains("java.lang.Integer cannot be cast to class java.lang.String");
        verifyNoInteractions(mockArchiveService);
    }

    // --- Tests for package-private method: archiveForDefaultDay ---

    @Test
    @DisplayName("archiveForDefaultDay: delegates to ArchiveService.performDefaultArchival")
    void archiveForDefaultDay_delegatesToService() {
        when(mockArchiveService.performDefaultArchival()).thenReturn(Mono.just(SUCCESS_SUMMARY));

        Mono<String> resultMono = archiveFunction.archiveForDefaultDay();

        StepVerifier.create(resultMono) // Testing the Mono directly here
                .expectNext(SUCCESS_SUMMARY)
                .verifyComplete();
        verify(mockArchiveService).performDefaultArchival();
    }

    // --- Tests for package-private method: archiveRangeEndingBeforeDateFromPayload ---

    @Test
    @DisplayName("archiveRangeEndingBeforeDateFromPayload: valid date, calls service with correct params")
    void archiveRangeEndingBeforeDateFromPayload_validDate_callsServiceCorrectly() {
        var localDate = LocalDate.of(2023, 5, 15);
        Map<String, Object> payload = Map.of("archiveBeforeDate", localDate.toString());

        doReturn(Mono.just(SUCCESS_SUMMARY))
                .when(mockArchiveService)
                .archiveOlderThan(localDate);

        var resultMono = archiveFunction.archiveRangeEndingBeforeDateFromPayload(payload);

        StepVerifier.create(resultMono)
                .expectNext(SUCCESS_SUMMARY)
                .verifyComplete();
        verify(mockArchiveService).archiveOlderThan(localDate);
    }

    @Test
    @DisplayName("archiveRangeEndingBeforeDateFromPayload: invalid date string, throws DateTimeParseException")
    void archiveRangeEndingBeforeDateFromPayload_invalidDateString_throwsException() {
        Map<String, Object> payload = Map.of("archiveBeforeDate", "not-a-date");

        assertThrows(DateTimeParseException.class, () -> {
            archiveFunction.archiveRangeEndingBeforeDateFromPayload(payload).block();
        });
        verifyNoInteractions(mockArchiveService);
    }

    @Test
    @DisplayName("archiveRangeEndingBeforeDateFromPayload: null date string, throws ClassCastException or NPE")
    void archiveRangeEndingBeforeDateFromPayload_nullDateString_throwsException() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("archiveBeforeDate", null);

        assertThrows(NullPointerException.class, () -> {
            archiveFunction.archiveRangeEndingBeforeDateFromPayload(payload).block();
        });
        verifyNoInteractions(mockArchiveService);
    }
}
