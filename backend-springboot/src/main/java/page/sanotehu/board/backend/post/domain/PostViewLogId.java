package page.sanotehu.board.backend.post.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PostViewLogId implements Serializable {
    private Long postId;
    private Long memberId;
    private String ipAddress;
    private LocalDate viewedOn;
}