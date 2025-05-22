package dev.devanks.solarman.archiver.function;

import com.google.common.annotations.VisibleForTesting;
import dev.devanks.solarman.archiver.service.ArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArchiveFunction {

    private final ArchiveService archiveService;

    /**
     * Main function bean: firestoreArchiver.
     */
    @Bean
    public Function<HashMap<String, Object>, String> firestoreArchiver() {
        return payload -> {
            log.info("firestoreArchiver function triggered with payload: {}", payload);

            if (payload == null || payload.isEmpty() || !payload.containsKey("archiveBeforeDate")) {
                return archiveForDefaultDay().block();
            }

            return archiveForGivenDay(payload);
        };
    }

    /**
     * Handles default archival: archives data for a single specific day that is 'daysOld' ago.
     */
    @VisibleForTesting
    Mono<String> archiveForDefaultDay() {
        log.info("Default archival: Delegating to ArchiveService.performDefaultArchival().");
        return archiveService.performDefaultArchival();
    }

    /**
     * Handles archival for a specific day: archives data for a range of 'daysOld' days,
     * ending strictly before the date specified in the payload's 'archiveBeforeDate'.
     */
    private String archiveForGivenDay(HashMap<String, Object> payload) {
        try {
            return archiveRangeEndingBeforeDateFromPayload(payload).block();
        } catch (Exception e) {
            log.error("Error processing 'archiveBeforeDate' from payload: {}. Details: {}", payload, e.getMessage(), e);
            return "Error: Invalid 'archiveBeforeDate' in payload. Use YYYY-MM-DD format. Details: " + e.getMessage();
        }
    }

    /**
     * Handles payload-driven archival: archives data for a range of 'daysOld' days,
     * ending strictly before the date specified in the payload's 'archiveBeforeDate'.
     */
    @VisibleForTesting
    Mono<String> archiveRangeEndingBeforeDateFromPayload(Map<String, Object> payload) {
        var dateStr = (String) payload.get("archiveBeforeDate");
        var payloadExclusiveEndDate = LocalDate.parse(dateStr);


        log.info("Payload-driven archival: Requesting archive for data strictly before {}. ", payloadExclusiveEndDate);

        return archiveService.archiveOlderThan(payloadExclusiveEndDate);
    }
}
