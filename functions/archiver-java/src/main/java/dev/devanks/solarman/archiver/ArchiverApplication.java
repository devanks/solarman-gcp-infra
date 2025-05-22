package dev.devanks.solarman.archiver;

import com.google.cloud.spring.data.firestore.repository.config.EnableReactiveFirestoreRepositories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableReactiveFirestoreRepositories
public class ArchiverApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArchiverApplication.class, args);
    }

//    @Bean
//    public Function<Map<String, Object>, Mono<String>> firestoreArchiver(ArchiveFunction function) {
//    public Supplier<String> firestoreArchiver(ArchiveFunction function) {
//        return () -> function.firestoreArchiver().apply(emptyMap()).block();
//    }
}
