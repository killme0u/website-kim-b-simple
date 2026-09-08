package page.sanotehu.board.backend.comment.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CommentCommand {
    @NotBlank
    private String content;
}