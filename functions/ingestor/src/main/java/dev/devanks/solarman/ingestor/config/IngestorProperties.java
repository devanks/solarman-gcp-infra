// functions/ingestor/src/main/java/dev/devanks/solarman/ingestor/config/IngestorProperties.java
package dev.devanks.solarman.ingestor.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull; // For URL if needed
import org.hibernate.validator.constraints.URL; // More specific validation

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "ingestor") // Keep top-level prefix
@Slf4j
public class IngestorProperties {

    // Nested class for GCP specific properties
    @Data
    @Validated
    public static class GcpProperties {
        @NotEmpty
        private String firestoreLatestDocumentId = "latest";
        @NotEmpty
        private String solarmanApiToken; // Injected via sm://
    }

    // Nested class for Client specific properties
    @Data
    @Validated
    public static class ClientProperties {
        @NotNull // Use NotNull for nested properties class itself
        private SolarmanClientProperties solarman = new SolarmanClientProperties();
    }

    @Data
    @Validated
    public static class SolarmanClientProperties {
        @NotEmpty
        @URL // Validate it's a URL
        private String url; // Base URL like https://globalhome.solarmanpv.com
    }

    // Inject nested properties
    @NotNull
    private GcpProperties gcp = new GcpProperties();

    @NotNull
    private ClientProperties client = new ClientProperties();

}
