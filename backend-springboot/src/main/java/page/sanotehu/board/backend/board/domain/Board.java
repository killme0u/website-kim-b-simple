package page.sanotehu.board.backend.board.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import page.sanotehu.board.backend.common.AuthenticationRequiredException;
import page.sanotehu.board.backend.member.domain.Member;

import java.util.Optional;

@Entity
@Table(name = "board")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Board {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String slug;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "requires_auth_to_read", nullable = false)
    private boolean requiresAuthToRead = false;

    @Column(name = "requires_auth_to_write", nullable = false)
    private boolean requiresAuthToWrite;

    @Column(name = "allows_comment", nullable = false)
    private boolean allowsComment;

    @Column(name = "allows_attachment", nullable = false)
    private boolean allowsAttachment;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    public void checkReadable(Optional<Member> actor) {
        if (this.requiresAuthToRead && actor.isEmpty()) {
            throw new AuthenticationRequiredException("'%s' 게시판은 로그인 후 이용할 수 있습니다.".formatted(this.name));
        }
    }

    public void checkWritable(Optional<Member> actor) {
        if (this.requiresAuthToWrite && actor.isEmpty()) {
            throw new AuthenticationRequiredException("'%s' 게시판은 로그인 후 글을 쓸 수 있습니다.".formatted(this.name));
        }
    }
}