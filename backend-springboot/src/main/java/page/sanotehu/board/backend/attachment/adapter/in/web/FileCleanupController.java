package page.sanotehu.board.backend.attachment.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import page.sanotehu.board.backend.attachment.application.OrphanedFileCleanupService;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/files")
@RequiredArgsConstructor
public class FileCleanupController {

    private final OrphanedFileCleanupService cleanupService;

    @PostMapping("/cleanup-orphaned")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> cleanupOrphanedFiles() {
        cleanupService.cleanupOrphanedFiles();
        return Map.of("status", "cleanup_started");
    }
}
