package dev.devanks.solarman.archiver.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import dev.devanks.solarman.archiver.entity.SolarReadingHistoryEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface SolarReadingHistoryRepository extends FirestoreReactiveRepository<SolarReadingHistoryEntity> {
}
