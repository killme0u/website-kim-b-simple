package page.sanotehu.board.backend.attachment.application;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import page.sanotehu.board.backend.attachment.adapter.in.web.dto.FileResponse;
import page.sanotehu.board.backend.attachment.domain.MediaKind;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${app.storage.root}")
    private String storageLocation;

    private Path rootPath;

    @PostConstruct
    public void init() {
        rootPath = Paths.get(storageLocation).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage location", e);
        }
    }

    public FileResponse storeFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) originalFilename = "unknown";
        String ext = "";
        int extIndex = originalFilename.lastIndexOf(".");
        if (extIndex > 0) {
            ext = originalFilename.substring(extIndex); // implies extension with dot
        }
        
        String storedName = UUID.randomUUID().toString() + ext;
        Path targetLocation = rootPath.resolve(storedName);

        try {
            file.transferTo(targetLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not store file " + originalFilename, e);
        }

        String contentType = file.getContentType();
        if (contentType == null) contentType = "application/octet-stream";

        return FileResponse.builder()
                .originalName(originalFilename)
                .storedName(storedName)
                .contentType(contentType)
                .mediaKind(MediaKind.from(contentType))
                .byteSize(file.getSize())
                .build();
    }

    public Resource loadFileAsResource(String storedName) {
        Path filePath = rootPath.resolve(storedName).normalize();
        if (!filePath.startsWith(rootPath)) {
            throw new SecurityException("Path traversal attempt");
        }
        Resource resource = new FileSystemResource(filePath.toFile());
        if (resource.exists() && resource.isReadable()) {
            return resource;
        } else {
            throw new RuntimeException("File not found " + storedName);
        }
    }
}