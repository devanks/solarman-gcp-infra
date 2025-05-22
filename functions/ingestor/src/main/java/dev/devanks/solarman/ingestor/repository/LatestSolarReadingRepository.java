// functions/ingestor/src/main/java/dev/devanks/solarman/ingestor/repository/LatestSolarReadingRepository.java
package dev.devanks.solarman.ingestor.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import dev.devanks.solarman.ingestor.entity.LatestSolarReadingEntity;
import org.springframework.stereotype.Repository;

@Repository // Optional but good practice
public interface LatestSolarReadingRepository extends FirestoreReactiveRepository<LatestSolarReadingEntity> {
    // No need to specify the ID type <LatestSolarReadingEntity, String> - it's inferred
    // Can add custom query methods here if needed later
}
