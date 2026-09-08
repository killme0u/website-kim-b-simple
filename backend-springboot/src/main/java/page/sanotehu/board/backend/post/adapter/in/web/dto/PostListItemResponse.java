package page.sanotehu.board.backend.post.adapter.in.web.dto;

import lombok.Builder;
import lombok.Data;
import page.sanotehu.board.backend.post.domain.Post;
import java.time.ZonedDateTime;

@Data
@Builder
public class PostListItemResponse {
    private Long id;
    private String title;
    private String authorName;
    private int viewCount;
    private int likeCount;
    private ZonedDateTime createdAt;

    public static PostListItemResponse from(Post p) {
        return PostListItemResponse.builder()
                .id(p.getId())
                .title(p.getTitle())
                .authorName(p.getMember() != null ? p.getMember().getUsername() : p.getGuestNickname())
                .viewCount(p.getViewCount())
                .likeCount(p.getLikeCount())
                .createdAt(p.getCreatedAt())
                .build();
    }
}