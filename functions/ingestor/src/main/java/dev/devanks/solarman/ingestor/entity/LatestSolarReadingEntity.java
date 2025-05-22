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
@Builder // Add builder
@NoArgsConstructor
@AllArgsConstructor
// Use property placeholder for collection name
@Document(collectionName = "latest_solar_reading")
public class LatestSolarReadingEntity {

    @DocumentId // Identifies the document ID field
    private String id; // Will be set to the fixed 'latest' ID

    // Fields stored in Firestore (match SolarReadingPayload)
    // @PropertyName not strictly needed if field names match Firestore document fields
    private double dailyProductionKWh;
    private double currentPowerW;
    private boolean isOnline;
    private Instant readingTimestamp;
    private Instant ingestedTimestamp;
}
