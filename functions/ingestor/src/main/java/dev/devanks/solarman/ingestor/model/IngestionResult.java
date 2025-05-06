// functions/ingestor/src/main/java/dev/devanks/solarman/ingestor/model/IngestionResult.java
package dev.devanks.solarman.ingestor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // Don't include null fields (like errorDetails on success)
public class IngestionResult {

    public enum Status {
        SUCCESS, FAILURE
    }

    private Status status;
    private String message;
    private Long durationMs;
    private String latestDocumentId;
    private String historyDocumentId;
    private String errorDetails; // Only populated on failure

}
