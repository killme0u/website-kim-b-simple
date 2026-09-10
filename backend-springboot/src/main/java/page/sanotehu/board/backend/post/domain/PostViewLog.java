package page.sanotehu.board.backend.post.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(name = "post_view_log")
@IdClass(PostViewLogId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostViewLog {

    @Id
    @Column(name = "post_id")
    private Long postId;

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Id
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Id
    @Column(name = "viewed_on")
    private LocalDate viewedOn;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private ZonedDateTime createdAt;

    public PostViewLog(Long postId, Long memberId, LocalDate viewedOn) {
        this.postId = postId;
        this.memberId = memberId;
        this.ipAddress = null;
        this.viewedOn = viewedOn;
        this.createdAt = ZonedDateTime.now();
    }

    public PostViewLog(Long postId, String ipAddress, LocalDate viewedOn) {
        this.postId = postId;
        this.memberId = null;
        this.ipAddress = ipAddress;
        this.viewedOn = viewedOn;
        this.createdAt = ZonedDateTime.now();
    }
}