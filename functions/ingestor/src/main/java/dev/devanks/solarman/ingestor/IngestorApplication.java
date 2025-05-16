// functions/ingestor/src/main/java/dev/devanks/solarman/ingestor/IngestorApplication.java
package dev.devanks.solarman.ingestor;

import com.google.cloud.spring.data.firestore.repository.config.EnableReactiveFirestoreRepositories;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
@EnableReactiveFirestoreRepositories
@Slf4j
public class IngestorApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestorApplication.class, args);
    }

}
