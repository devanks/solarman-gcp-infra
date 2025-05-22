// functions/ingestor/src/main/java/dev/devanks/solarman/ingestor/repository/SolarReadingHistoryRepository.java
package dev.devanks.solarman.ingestor.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import dev.devanks.solarman.ingestor.entity.SolarReadingHistoryEntity;
import org.springframework.stereotype.Repository;

@Repository // Optional
public interface SolarReadingHistoryRepository extends FirestoreReactiveRepository<SolarReadingHistoryEntity> {
    // Can add custom query methods here if needed later
}
