package dev.devanks.solarman.archiver.entity;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collectionName = "solar_readings_daily")
public class SolarReadingDailyEntity {
    @DocumentId
    @NonNull
    private String id;
    private Instant date;
    private double dailyProductionKWh;
    private double averagePowerW;
    private double maxPowerH;
}
