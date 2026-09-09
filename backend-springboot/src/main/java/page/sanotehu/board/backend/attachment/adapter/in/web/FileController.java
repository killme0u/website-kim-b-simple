package page.sanotehu.board.backend.attachment.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import page.sanotehu.board.backend.attachment.adapter.in.web.dto.FileResponse;
import page.sanotehu.board.backend.attachment.application.FileStorageService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String storedName,
            @RequestParam(required = false) String download) throws IOException {
        Resource resource = fileStorageService.loadFileAsResource(storedName);
        String filename = resource.getFilename() != null ? resource.getFilename() : storedName;

        ContentDisposition disposition = (download != null
                ? ContentDisposition.attachment()
                : ContentDisposition.inline())
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(probeContentType(resource))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    private MediaType probeContentType(Resource resource) throws IOException {
        Path path = resource.getFile().toPath();
        String contentType = Files.probeContentType(path);
        return contentType != null
                ? MediaType.parseMediaType(contentType)
                : MediaType.APPLICATION_OCTET_STREAM;
    }
}
