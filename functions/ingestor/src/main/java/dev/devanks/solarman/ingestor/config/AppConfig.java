// functions/ingestor/src/main/java/dev/devanks/solarman/ingestor/config/AppConfig.java
package dev.devanks.solarman.ingestor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IngestorProperties.class) // Link IngestorProperties to Spring context
public class AppConfig {
    // You can define other beans here if needed later
}
