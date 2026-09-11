package page.sanotehu.board.backend.post.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Modifying(clearAutomatically = true)
    @Query("update Post p set p.viewCount = p.viewCount + 1 where p.id = :id")
    void increaseViewCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount + 1 where p.id = :id")
    void increaseLikeCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount - 1 where p.id = :id and p.likeCount > 0")
    void decreaseLikeCount(@Param("id") Long id);

    @Query("SELECT p FROM Post p WHERE p.board.slug = :boardSlug AND p.deletedAt IS NULL AND " +
           "(COALESCE(:keyword, '') = '' OR p.title LIKE %:keyword% OR p.content LIKE %:keyword%)")
    Page<Post> search(@Param("boardSlug") String boardSlug, @Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.deletedAt IS NULL AND " +
           "(COALESCE(:keyword, '') = '' OR p.title LIKE %:keyword% OR p.content LIKE %:keyword%)")
    Page<Post> searchGlobal(@Param("keyword") String keyword, Pageable pageable);

    Page<Post> findByMemberIdAndDeletedAtIsNullOrderByIdDesc(Long memberId, Pageable pageable);
}