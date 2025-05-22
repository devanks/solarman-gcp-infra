// functions/ingestor/src/main/java/dev/devanks/solarman/ingestor/client/SolarmanApiClientConfig.java
package dev.devanks.solarman.ingestor.config;

import feign.Logger.Level;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;

import static feign.Logger.Level.BASIC;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.USER_AGENT;

@RequiredArgsConstructor
@Slf4j
public class SolarmanApiClientConfig {

    private final IngestorProperties ingestorProperties;

    @Bean
    public RequestInterceptor authorizationInterceptor() {
        return template -> {
            var token = getToken();

            log.debug("Adding Authorization header to Solarman API request.");
            template.header(AUTHORIZATION, "Bearer " + token);
            template.header(USER_AGENT, "GCP-Cloud-Function-Solarman-Ingestor-Java-Feign/1.0");
        };
    }

    private String getToken() {
        var token = ingestorProperties.getGcp().getSolarmanApiToken(); // Get token from nested props
        if (token == null || token.isBlank() || token.startsWith("sm://")) {
            // This should ideally not happen if sm:// resolution works
            log.error("Solarman API token is missing or unresolved. Cannot add Authorization header.");
            // Consider throwing an exception here if the token is absolutely required
            throw new IllegalStateException("Solarman API token not available for Feign client.");
        }
        return token;
    }

    // You could add other Feign customizations here (Logger.Level, Retryer, ErrorDecoder)
    @Bean
    public Level feignLoggerLevel() {
        return BASIC; // Or FULL for more details
    }
}
