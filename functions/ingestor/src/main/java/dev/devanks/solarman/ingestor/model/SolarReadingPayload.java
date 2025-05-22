// functions/ingestor/src/main/java/dev/devanks/solarman/ingestor/model/SolarReadingPayload.java
package dev.devanks.solarman.ingestor.model;

import com.google.cloud.firestore.annotation.PropertyName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolarReadingPayload {

    // Fields stored in Firestore
    @PropertyName("dailyProductionKWh") // Explicitly map to Firestore field name (good practice)
    private double dailyProductionKWh;

    @PropertyName("currentPowerW")
    private double currentPowerW;

    @PropertyName("isOnline")
    private boolean isOnline;

    @PropertyName("readingTimestamp") // Timestamp from the Solarman device/API reading
    private Instant readingTimestamp;

    @PropertyName("ingestedTimestamp") // Timestamp when the function processed the data
    private Instant ingestedTimestamp;

    // Note: We don't need @DocumentId here because we set the ID explicitly when writing.
}
