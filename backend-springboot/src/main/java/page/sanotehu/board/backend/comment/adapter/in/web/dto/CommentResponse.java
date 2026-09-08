package page.sanotehu.board.backend.comment.adapter.in.web.dto;

import lombok.Builder;
import lombok.Data;
import page.sanotehu.board.backend.comment.domain.Comment;

import java.time.ZonedDateTime;

@Data
@Builder
public class CommentResponse {
    private Long id;
    private Long postId;
    private String authorName;
    private String content;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private boolean isOwner;

    public static CommentResponse from(Comment c, Long currentMemberId, boolean isAdmin) {
        boolean owner = isAdmin || (c.getMember() != null && currentMemberId != null && c.getMember().getId().equals(currentMemberId));
        return CommentResponse.builder()
                .id(c.getId())
                .postId(c.getPost().getId())
                .authorName(c.getMember().getUsername()) // since guest comment was disabled, getMember() is not null
                .content(c.getContent())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .isOwner(owner)
                .build();
    }
}