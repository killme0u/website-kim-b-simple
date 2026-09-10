package page.sanotehu.board.backend.attachment.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import page.sanotehu.board.backend.attachment.domain.AttachmentRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrphanedFileCleanupService {

    private static final long ORPHAN_RETENTION_MILLIS = 3600000; // 1 hour

    @Value("${app.storage.root}")
    private String storageLocation;

    private final AttachmentRepository attachmentRepository;

    @Scheduled(cron = "0 0 0 * * *") // Run at midnight every day
    public void cleanupOrphanedFiles() {
        try {
            Path storagePath = Paths.get(storageLocation).toAbsolutePath().normalize();
            if (!Files.exists(storagePath) || !Files.isDirectory(storagePath)) {
                log.warn("Storage location does not exist or is not a directory: {}", storagePath);
                return;
            }

            Set<String> trackedFiles = new HashSet<>(
                    attachmentRepository.findAll().stream()
                            .map(a -> a.getStoredName())
                            .toList()
            );

            long now = System.currentTimeMillis();
            File storageDir = storagePath.toFile();
            File[] files = storageDir.listFiles();

            if (files == null) {
                log.warn("Could not list files in storage directory: {}", storagePath);
                return;
            }

            for (File file : files) {
                if (file.isFile() && !trackedFiles.contains(file.getName())) {
                    long fileAge = now - file.lastModified();
                    if (fileAge > ORPHAN_RETENTION_MILLIS) {
                        if (file.delete()) {
                            log.info("Deleted orphaned file: {}", file.getName());
                        } else {
                            log.warn("Failed to delete orphaned file: {}", file.getName());
                        }
                    } else {
                        log.debug("Retaining recently uploaded orphaned file: {} (age: {}ms)",
                                file.getName(), fileAge);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error during orphaned file cleanup", e);
        }
    }
}
