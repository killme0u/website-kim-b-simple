package page.sanotehu.board.backend.post.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import page.sanotehu.board.backend.attachment.adapter.in.web.dto.FileResponse;
import java.util.List;
import java.util.ArrayList;

@Data
public class PostCommand {
    @NotBlank
    private String title;
    @NotBlank
    private String content;

    private String guestNickname;
    private String guestPassword;

    private List<FileResponse> attachments = new ArrayList<>();
}