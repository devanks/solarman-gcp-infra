// functions/ingestor/src/main/java/dev/devanks/solarman/ingestor/entity/LatestSolarReadingEntity.java
package dev.devanks.solarman.ingestor.entity;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collectionName = "latest_solar_reading")
public class LatestSolarReadingEntity {

    @DocumentId
    private String id; // Will be set to the fixed 'latest' ID
    private double dailyProductionKWh;
    private double currentPowerW;
    private boolean isOnline;
    private Instant readingTimestamp;
    private Instant ingestedTimestamp;
}
