package dev.devanks.solarman.archiver.entity;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.*;

import java.time.Instant;

import static java.util.Objects.requireNonNullElse;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collectionName = "solar_readings_history")
public class SolarReadingHistoryEntity {
    @DocumentId
    @Getter
    private String id;

    private Double dailyProductionKWh;
    private Double currentPowerW;
    private boolean isOnline;
    @Getter
    private Instant readingTimestamp;
    @Getter
    private Instant ingestedTimestamp;

    public Double getCurrentPowerW() {
        return requireNonNullElse(currentPowerW, 0.0);
    }

    public Double getDailyProductionKWh() {
        return requireNonNullElse(dailyProductionKWh, 0.0);
    }
}
