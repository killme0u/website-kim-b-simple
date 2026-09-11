package page.sanotehu.board.backend.comment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import page.sanotehu.board.backend.common.BaseTimeEntity;
import page.sanotehu.board.backend.member.domain.Member;
import page.sanotehu.board.backend.post.domain.Post;

import java.time.ZonedDateTime;

@Entity
@Table(name = "comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "deleted_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private ZonedDateTime deletedAt;

    public static Comment create(Post post, Member member, String content) {
        Comment c = new Comment();
        c.post = post;
        c.member = member;
        c.content = content;
        return c;
    }
    
    public void update(String content) {
        this.content = content;
    }

    public void softDelete() {
        this.deletedAt = ZonedDateTime.now();
    }
}