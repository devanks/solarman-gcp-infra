package dev.devanks.solarman.archiver.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import dev.devanks.solarman.archiver.entity.SolarReadingDailyEntity;

public interface SolarReadingDailyRepository extends FirestoreReactiveRepository<SolarReadingDailyEntity> {
}
