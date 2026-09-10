package page.sanotehu.board.backend.post.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;

public interface PostViewLogRepository extends JpaRepository<PostViewLog, PostViewLogId> {

    @Modifying
    @Query(value = """
        INSERT INTO post_view_log (post_id, member_id, ip_address, viewed_on)
        VALUES (:postId, :memberId, NULL, :viewedOn)
        ON CONFLICT DO NOTHING
        """, nativeQuery = true)
    int tryRecordMember(@Param("postId") Long postId,
                        @Param("memberId") Long memberId,
                        @Param("viewedOn") LocalDate viewedOn);

    @Modifying
    @Query(value = """
        INSERT INTO post_view_log (post_id, member_id, ip_address, viewed_on)
        VALUES (:postId, NULL, :ipAddress, :viewedOn)
        ON CONFLICT DO NOTHING
        """, nativeQuery = true)
    int tryRecordGuest(@Param("postId") Long postId,
                       @Param("ipAddress") String ipAddress,
                       @Param("viewedOn") LocalDate viewedOn);

    // 하위호환성을 위한 메서드 (deprecated)
    @Modifying
    @Query(value = """
        INSERT INTO post_view_log (post_id, member_id, ip_address, viewed_on)
        VALUES (:postId, :memberId, NULL, :viewedOn)
        ON CONFLICT DO NOTHING
        """, nativeQuery = true)
    int tryRecord(@Param("postId") Long postId,
                  @Param("memberId") Long memberId,
                  @Param("viewedOn") LocalDate viewedOn);
}