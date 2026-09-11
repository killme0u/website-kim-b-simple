package page.sanotehu.board.backend.attachment.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import page.sanotehu.board.backend.attachment.domain.MediaKind;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileResponse {
    private String originalName;
    private String storedName;
    private String contentType;
    private MediaKind mediaKind;
    private long byteSize;
}