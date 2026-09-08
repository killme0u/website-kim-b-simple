package page.sanotehu.board.backend.post.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PostUpdateCommand {
    @NotBlank
    private String title;
    @NotBlank
    private String content;
    private String guestPassword;
}