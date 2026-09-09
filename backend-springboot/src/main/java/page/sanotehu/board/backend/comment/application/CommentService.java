package page.sanotehu.board.backend.comment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.sanotehu.board.backend.comment.adapter.in.web.dto.CommentCommand;
import page.sanotehu.board.backend.comment.adapter.in.web.dto.CommentResponse;
import page.sanotehu.board.backend.comment.domain.Comment;
import page.sanotehu.board.backend.comment.domain.CommentRepository;
import page.sanotehu.board.backend.common.AuthenticationRequiredException;
import page.sanotehu.board.backend.member.application.CustomUserDetails;
import page.sanotehu.board.backend.member.domain.Member;
import page.sanotehu.board.backend.member.domain.MemberRole;
import page.sanotehu.board.backend.post.domain.Post;
import page.sanotehu.board.backend.post.domain.PostRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(Long postId, CustomUserDetails user) {
        Post post = postRepository.findById(postId).filter(p -> p.getDeletedAt() == null).orElseThrow();
        Optional<Member> actor = Optional.ofNullable(user).map(CustomUserDetails::getMember);
        post.getBoard().checkReadable(actor);

        boolean isAdmin = actor.map(m -> m.getRole() == MemberRole.ADMIN).orElse(false);
        Long memberId = actor.map(Member::getId).orElse(null);

        return commentRepository.findByPostIdAndDeletedAtIsNullOrderByIdAsc(postId).stream()
                .map(c -> CommentResponse.from(c, memberId, isAdmin))
                .toList();
    }

    @Transactional
    public Long createComment(Long postId, CommentCommand cmd, CustomUserDetails user) {
        if (user == null) {
            throw new AuthenticationRequiredException("Login required to comment");
        }
        
        Post post = postRepository.findById(postId).filter(p -> p.getDeletedAt() == null).orElseThrow();
        if (!post.getBoard().isAllowsComment()) {
            throw new IllegalArgumentException("Board doesn't allow comments");
        }
        post.getBoard().checkWritable(Optional.of(user.getMember()));

        Comment comment = Comment.create(post, user.getMember(), cmd.getContent());
        commentRepository.save(comment);
        return comment.getId();
    }

    @Transactional
    public void updateComment(Long id, CommentCommand cmd, CustomUserDetails user) {
        if (user == null) throw new AuthenticationRequiredException("Login required");

        Comment comment = commentRepository.findById(id).filter(c -> c.getDeletedAt() == null).orElseThrow();
        if (user.getMember().getRole() != MemberRole.ADMIN && !comment.getMember().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not the author");
        }

        comment.update(cmd.getContent());
    }

    @Transactional
    public void deleteComment(Long id, CustomUserDetails user) {
        if (user == null) throw new AuthenticationRequiredException("Login required");

        Comment comment = commentRepository.findById(id).filter(c -> c.getDeletedAt() == null).orElseThrow();
        if (user.getMember().getRole() != MemberRole.ADMIN && !comment.getMember().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not the author");
        }

        comment.softDelete();
    }
}