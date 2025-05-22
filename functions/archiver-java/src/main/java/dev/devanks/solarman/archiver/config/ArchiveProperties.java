package dev.devanks.solarman.archiver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "archival")
@Data
public class ArchiveProperties {
    /**
     * Number of days old for data to be considered for archival.
     */
    private int daysOld = 30; // Default to 30 days

    /**
     * Name of the GCS bucket where archives will be stored.
     */
    private String bucketName;

    /**
     * Flag to control whether documents are actually deleted from Firestore after archival.
     * Defaults to false for safety. Set to true in production when confident.
     */
    private boolean deletionEnabled = false; // Default to false
}
