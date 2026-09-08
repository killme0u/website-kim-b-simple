package page.sanotehu.board.backend.attachment.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import page.sanotehu.board.backend.attachment.adapter.in.web.dto.FileResponse;
import page.sanotehu.board.backend.attachment.application.FileStorageService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileResponse uploadFile(@RequestParam("file") MultipartFile file) {
        return fileStorageService.storeFile(file);
    }

    @GetMapping("/{storedName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String storedName, @RequestParam(required = false) String download) throws IOException {
        Resource resource = fileStorageService.loadFileAsResource(storedName);
        
        HttpHeaders headers = new HttpHeaders();
        // Simple MIME guessing or relying on saved content type in DB
        // But we only have storedName here. We can rely on extension or probe
        String contentType = java.nio.file.Files.probeContentType(resource.getFile().toPath());
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        
        if (download != null) {
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + URLEncoder.encode(resource.getFilename(), StandardCharsets.UTF_8) + "\"");
        } else {
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .headers(headers)
                .body(resource);
    }
}