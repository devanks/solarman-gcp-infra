// functions/ingestor/src/main/java/dev/devanks/solarman/ingestor/IngestorApplication.java
package dev.devanks.solarman.ingestor;

import com.google.cloud.spring.data.firestore.repository.config.EnableReactiveFirestoreRepositories;
import dev.devanks.solarman.ingestor.model.IngestionResult; // <<< Import Result POJO
import dev.devanks.solarman.ingestor.service.IngestorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

import java.util.function.Supplier;

@SpringBootApplication
@EnableFeignClients
@EnableReactiveFirestoreRepositories
@Slf4j
public class IngestorApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestorApplication.class, args);
    }

    /**
     * Defines the Spring Cloud Function bean executed by GcfJarLauncher.
     * This supplier delegates the work to the IngestorService and returns
     * a structured IngestionResult.
     *
     * @param ingestorService The service containing the core ingestion logic.
     * @return A Supplier bean that performs the ingestion and returns IngestionResult.
     */
    @Bean
    public Supplier<IngestionResult> ingestSolarDataFunction(IngestorService ingestorService) {
        log.info("Creating Supplier bean: ingestSolarDataFunction");
        // The lambda IS the function executed. It calls the service method.
        return () -> {
            try {
                // Delegate to the service which now returns the POJO
                return ingestorService.performIngestion();
            } catch (Exception e) {
                // Catch any totally unexpected errors during service call invocation
                // (though the service itself now catches and returns failure POJOs)
                log.error("Unexpected error invoking IngestorService from Supplier", e);
                return IngestionResult.builder()
                        .status(IngestionResult.Status.FAILURE)
                        .message("Unexpected framework/invocation error: " + e.getMessage())
                        .errorDetails(e.getClass().getName() + ": " + e.getMessage())
                        .build();
            }
        };
    }
}
