package page.sanotehu.board.backend.attachment.adapter.in.web.dto;

import lombok.Builder;
import lombok.Data;
import page.sanotehu.board.backend.attachment.domain.MediaKind;

@Data
@Builder
public class FileResponse {
    private String originalName;
    private String storedName;
    private String contentType;
    private MediaKind mediaKind;
    private long byteSize;
}