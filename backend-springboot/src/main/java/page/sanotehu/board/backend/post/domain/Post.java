package page.sanotehu.board.backend.post.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import page.sanotehu.board.backend.board.domain.Board;
import page.sanotehu.board.backend.common.BaseTimeEntity;
import page.sanotehu.board.backend.member.domain.Member;
import page.sanotehu.board.backend.member.domain.MemberRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.ZonedDateTime;
import java.util.Optional;

@Entity
@Table(name = "post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "guest_nickname", length = 30)
    private String guestNickname;

    @Column(name = "guest_password_hash", length = 100)
    private String guestPasswordHash;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    @Column(name = "deleted_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private ZonedDateTime deletedAt;

    public static Post guest(Board board, String guestNickname, String guestPasswordHash, String title, String content) {
        Post post = new Post();
        post.board = board;
        post.guestNickname = guestNickname;
        post.guestPasswordHash = guestPasswordHash;
        post.title = title;
        post.content = content;
        return post;
    }

    public static Post member(Board board, Member member, String title, String content) {
        Post post = new Post();
        post.board = board;
        post.member = member;
        post.title = title;
        post.content = content;
        return post;
    }

    public void checkEditable(Optional<Member> actor, String rawGuestPassword, PasswordEncoder encoder) {
        if (actor.isPresent()) {
            Member m = actor.get();
            if (m.getRole() == MemberRole.ADMIN) return;
            if (this.member != null && this.member.getId().equals(m.getId())) return;
            throw new AccessDeniedException("작성자만 수정·삭제할 수 있습니다.");
        }
        if (this.member != null) {
            throw new AccessDeniedException("회원이 작성한 글은 로그인 후 수정·삭제할 수 있습니다.");
        }
        if (rawGuestPassword == null || !encoder.matches(rawGuestPassword, this.guestPasswordHash)) {
            throw new AccessDeniedException("작성자 비밀번호가 일치하지 않습니다.");
        }
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public void softDelete() {
        this.deletedAt = ZonedDateTime.now();
    }
}