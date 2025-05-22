package dev.devanks.solarman.archiver.service;

import dev.devanks.solarman.archiver.config.ArchiveProperties;
import dev.devanks.solarman.archiver.entity.SolarReadingHistoryEntity;
import dev.devanks.solarman.archiver.service.io.GcsResourceProvider;
import dev.devanks.solarman.archiver.service.io.GcsWritableResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;
import java.util.zip.GZIPOutputStream;

import static org.springframework.util.ObjectUtils.isEmpty;
import static reactor.core.scheduler.Schedulers.boundedElastic;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageWriterService {

    private final ArchiveProperties properties;
    private final GcsResourceProvider resourceProvider;

    private static final String CSV_HEADER = "id,readingTimestamp,ingestedTimestamp,isOnline,currentPowerW,dailyProductionKWh";

    public Mono<String> archiveEntitiesToGCS(List<SolarReadingHistoryEntity> entitiesToArchive, LocalDate archiveForDate) {
        if (isEmpty(entitiesToArchive)) {
            log.warn("No entities provided to archive for date {}.", archiveForDate);
            return Mono.empty();
        }
        if (archiveForDate == null) {
            log.error("archiveForDate cannot be null when archiving entities.");
            return Mono.error(new IllegalArgumentException("archiveForDate cannot be null."));
        }

        String archiveDir = String.format("archive/%s/%s/%s/",
                archiveForDate.format(DateTimeFormatter.ofPattern("yyyy")),
                archiveForDate.format(DateTimeFormatter.ofPattern("MM")),
                archiveForDate.format(DateTimeFormatter.ofPattern("dd"))
        );
        String timestampSuffix = Instant.now().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("HHmmss-SSSSSS"));
        String filename = String.format("firestore-export-%s-%s.csv.gz",
                archiveForDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                timestampSuffix
        );
        String gcsPath = "gs://" + properties.getBucketName() + "/" + archiveDir + filename;

        return Mono.fromCallable(() -> {
                    log.info("Attempting to archive {} entities for date {} to {} (CSV format) on thread: {}",
                            entitiesToArchive.size(), archiveForDate, gcsPath, Thread.currentThread().getName());

                    GcsWritableResource resource = resourceProvider.createWritableResource(gcsPath);

                    try (OutputStream os = resource.getOutputStream();
                         GZIPOutputStream gzipOs = new GZIPOutputStream(os);
                         PrintWriter writer = new PrintWriter(new OutputStreamWriter(gzipOs, StandardCharsets.UTF_8))) {

                        writer.println(CSV_HEADER); // Always write the header

                        for (SolarReadingHistoryEntity entity : entitiesToArchive) {
                            writer.println(convertToCsvRow(entity));
                        }
                        writer.flush();
                    }
                    log.info("Successfully wrote {} entities for date {} to GCS (CSV): {} from thread: {}",
                            entitiesToArchive.size(), archiveForDate, gcsPath, Thread.currentThread().getName());
                    return gcsPath;
                })
                .subscribeOn(boundedElastic())
                .doOnError(e -> log.error("GCS CSV write failed for date {} path {}: {} (occurred on thread: {})",
                        archiveForDate, gcsPath, e.getMessage(), Thread.currentThread().getName(), e))
                .onErrorMap(e -> {
                    if (e instanceof RuntimeException && e.getMessage().startsWith("GCS write failed for path")) {
                        return e;
                    }
                    return new RuntimeException("GCS write failed for path " + gcsPath, e);
                });
    }

    private String convertToCsvRow(SolarReadingHistoryEntity entity) {
        StringJoiner joiner = new StringJoiner(",");
        joiner.add(escapeCsvField(entity.getId()));
        joiner.add(escapeCsvField(entity.getReadingTimestamp() != null ? entity.getReadingTimestamp().toString() : ""));
        joiner.add(escapeCsvField(entity.getIngestedTimestamp() != null ? entity.getIngestedTimestamp().toString() : ""));
        joiner.add(String.valueOf(entity.isOnline()));
        joiner.add(entity.getCurrentPowerW().toString());
        joiner.add(entity.getDailyProductionKWh().toString());
        return joiner.toString();
    }

    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        if (field.contains(",") || field.contains("\n") || field.contains("\"")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
