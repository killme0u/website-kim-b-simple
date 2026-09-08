package page.sanotehu.board.backend.post.adapter.in.web.dto;

import lombok.Builder;
import lombok.Data;
import page.sanotehu.board.backend.post.domain.Post;
import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
public class PostResponse {
    private Long id;
    private String boardSlug;
    private String title;
    private String content;
    private String authorName;
    private int viewCount;
    private int likeCount;
    private ZonedDateTime createdAt;
    private boolean isOwner;
    private List<page.sanotehu.board.backend.attachment.adapter.in.web.dto.FileResponse> attachments;

    public static PostResponse from(Post p, Long currentMemberId, boolean isAdmin, List<page.sanotehu.board.backend.attachment.adapter.in.web.dto.FileResponse> atts) {
        boolean owner = false;
        if (isAdmin) {
            owner = true;
        } else if (p.getMember() != null && currentMemberId != null && p.getMember().getId().equals(currentMemberId)) {
            owner = true;
        } else if (p.getMember() == null && currentMemberId == null) {
            owner = true; 
        }
        
        return PostResponse.builder()
                .id(p.getId())
                .boardSlug(p.getBoard().getSlug())
                .title(p.getTitle())
                .content(p.getContent())
                .authorName(p.getMember() != null ? p.getMember().getUsername() : p.getGuestNickname())
                .viewCount(p.getViewCount())
                .likeCount(p.getLikeCount())
                .createdAt(p.getCreatedAt())
                .isOwner(owner)
                .attachments(atts)
                .build();
    }
}