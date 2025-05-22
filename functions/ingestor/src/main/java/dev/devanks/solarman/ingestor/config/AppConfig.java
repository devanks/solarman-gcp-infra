// functions/ingestor/src/main/java/dev/devanks/solarman/ingestor/config/AppConfig.java
package dev.devanks.solarman.ingestor.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@Slf4j // Add logger
public class AppConfig {

    // Define the WebClient bean here
    @Bean
    public WebClient webClient() {
        log.info("Initializing WebClient bean.");
        // You can add default headers, timeouts, etc. here if needed
        return WebClient.builder().build();
    }

    // No need for Firestore, SecretManagerService, CredentialsProvider beans anymore
}
