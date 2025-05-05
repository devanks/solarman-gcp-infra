// functions/ingestor/src/main/java/dev/devanks/solarman/ingestor/config/IngestorProperties.java
package dev.devanks.solarman.ingestor.config;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;


@Data
@Validated // Enable validation of properties
@Configuration
@ConfigurationProperties(prefix = "ingestor.gcp") // Group properties under 'ingestor.gcp'
public class IngestorProperties {

    /**
     * GCP Project ID where the function and resources reside.
     * Usually auto-detected by GCP environment, but good to have as explicit config.
     */
    private String projectId; // Optional: Often detected automatically

    @NotEmpty
    private String firestoreLatestCollection = "latest_solar_reading";

    @NotEmpty
    private String firestoreHistoryCollection = "solar_readings_history";

    @NotEmpty
    private String firestoreLatestDocumentId = "latest";

    @NotEmpty
    private String solarmanApiUrl = "https://globalhome.solarmanpv.com/maintain-s/fast/system/3340235"; // Default or your specific URL

    /**
     * Secret Manager Secret ID (just the ID, not the full path).
     */
    @NotEmpty
    private String solarmanSecretId = "solarman-bearer-token";

    /**
     * Secret Manager Secret Version (e.g., "latest" or a specific number).
     */
    @NotEmpty
    private String solarmanSecretVersion = "latest";

    // Derived property to get the full secret version name
    public String getFullSecretVersionName() {
        String effectiveProjectId = projectId;
        if (effectiveProjectId == null || effectiveProjectId.isEmpty()) {
            // Attempt to auto-detect if not explicitly set (Requires GCP Core library or metadata server access)
            // For simplicity in Cloud Functions, rely on it being set via env var or default GCP detection.
            // Or throw an error if required and not found.
            // Let's assume for now it will be available via environment.
            // We'll handle potential null projectId later if needed.
            // Consider using com.google.cloud.ServiceOptions.getDefaultProjectId() if needed.
        }
        if (effectiveProjectId == null || effectiveProjectId.isEmpty()) {
            throw new IllegalStateException("GCP Project ID is not configured and could not be auto-detected.");
        }
        return String.format("projects/%s/secrets/%s/versions/%s", effectiveProjectId, solarmanSecretId, solarmanSecretVersion);
    }
}
