package page.sanotehu.board.backend.post.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "post_like")
@IdClass(PostLikeId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLike {

    @Id
    @Column(name = "post_id")
    private Long postId;

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private ZonedDateTime createdAt;

    public PostLike(Long postId, Long memberId) {
        this.postId = postId;
        this.memberId = memberId;
        this.createdAt = ZonedDateTime.now();
    }
}