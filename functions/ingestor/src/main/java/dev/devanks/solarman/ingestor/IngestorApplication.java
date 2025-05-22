// functions/ingestor/src/main/java/dev/devanks/solarman/ingestor/IngestorApplication.java
package dev.devanks.solarman.ingestor;

import com.google.cloud.spring.data.firestore.repository.config.EnableReactiveFirestoreRepositories;
import dev.devanks.solarman.ingestor.service.IngestorService; // <<< Import IngestorService
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean; // <<< Import Bean

import java.util.function.Supplier; // <<< Import Supplier

@SpringBootApplication
@EnableFeignClients
@EnableReactiveFirestoreRepositories
public class IngestorApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestorApplication.class, args);
    }

    /**
     * Defines the Spring Cloud Function bean that will be executed by GcfJarLauncher.
     * This supplier takes no input and returns a String result message.
     * It delegates the actual work to the IngestorService.
     *
     * @param ingestorService The service containing the core ingestion logic (auto-injected by Spring).
     * @return A Supplier bean that performs the ingestion.
     */
    @Bean
    public Supplier<String> ingestSolarDataFunction(IngestorService ingestorService) {
        // The lambda returned here IS the function that gets executed on trigger.
        return () -> {
            try {
                // Delegate the core logic to the service layer
                return ingestorService.performIngestion();
            } catch (Exception e) {
                // Ensure we catch any unexpected exceptions from the service
                // and return an error message. Logging happens within the service.
                // Returning the exception message might be helpful for quick diagnosis.
                // Consider returning a more generic error in production if messages leak sensitive info.
                return "Ingestion failed: " + e.getMessage();
            }
        };
    }
}
