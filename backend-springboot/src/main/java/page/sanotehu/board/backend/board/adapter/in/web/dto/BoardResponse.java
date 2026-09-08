package page.sanotehu.board.backend.board.adapter.in.web.dto;

import lombok.Builder;
import lombok.Data;
import page.sanotehu.board.backend.board.domain.Board;

@Data
@Builder
public class BoardResponse {
    private String slug;
    private String name;
    private boolean requiresAuthToRead;
    private boolean requiresAuthToWrite;
    private boolean allowsComment;
    private boolean allowsAttachment;

    public static BoardResponse from(Board b) {
        return BoardResponse.builder()
                .slug(b.getSlug())
                .name(b.getName())
                .requiresAuthToRead(b.isRequiresAuthToRead())
                .requiresAuthToWrite(b.isRequiresAuthToWrite())
                .allowsComment(b.isAllowsComment())
                .allowsAttachment(b.isAllowsAttachment())
                .build();
    }
}