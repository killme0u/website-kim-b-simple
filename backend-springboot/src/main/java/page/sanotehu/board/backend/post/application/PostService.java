package page.sanotehu.board.backend.post.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.sanotehu.board.backend.attachment.adapter.in.web.dto.FileResponse;
import page.sanotehu.board.backend.attachment.domain.Attachment;
import page.sanotehu.board.backend.attachment.domain.AttachmentRepository;
import page.sanotehu.board.backend.board.domain.Board;
import page.sanotehu.board.backend.board.domain.BoardRepository;
import page.sanotehu.board.backend.common.AuthenticationRequiredException;
import page.sanotehu.board.backend.member.application.CustomUserDetails;
import page.sanotehu.board.backend.member.domain.Member;
import page.sanotehu.board.backend.member.domain.MemberRole;
import page.sanotehu.board.backend.post.adapter.in.web.dto.*;
import page.sanotehu.board.backend.post.domain.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final PostViewLogRepository viewLogRepository;
    private final PostLikeRepository likeRepository;
    private final AttachmentRepository attachmentRepository;
    private final PasswordEncoder encoder;

    @Transactional(readOnly = true)
    public Page<PostListItemResponse> listPosts(String boardSlug, String keyword, Pageable pageable, CustomUserDetails user) {
        Board board = boardRepository.findBySlug(boardSlug).orElseThrow();
        Optional<Member> actor = actorOf(user);
        board.checkReadable(actor);
        return postRepository.search(boardSlug, keyword, pageable).map(PostListItemResponse::from);
    }

    @Transactional
    public PostResponse getPost(Long id, CustomUserDetails user) {
        Post post = postRepository.findById(id).filter(p -> p.getDeletedAt() == null).orElseThrow();
        Optional<Member> actor = actorOf(user);
        post.getBoard().checkReadable(actor);

        if (actor.isPresent()) {
            if (viewLogRepository.tryRecord(id, actor.get().getId(), LocalDate.now()) > 0) {
                postRepository.increaseViewCount(id);
            }
        } else {
            postRepository.increaseViewCount(id);
        }

        boolean isAdmin = actor.map(m -> m.getRole() == MemberRole.ADMIN).orElse(false);
        Long memberId = actor.map(Member::getId).orElse(null);

        List<FileResponse> atDtos = attachmentRepository.findByPostId(id).stream()
            .map(a -> FileResponse.builder()
                .originalName(a.getOriginalName())
                .storedName(a.getStoredName())
                .contentType(a.getContentType())
                .mediaKind(a.getMediaKind())
                .byteSize(a.getByteSize())
                .build())
            .toList();

        return PostResponse.from(post, memberId, isAdmin, atDtos);
    }

    @Transactional
    public Long createPost(String boardSlug, PostCommand cmd, CustomUserDetails user) {
        Board board = boardRepository.findBySlug(boardSlug).orElseThrow();
        Optional<Member> actor = actorOf(user);
        board.checkWritable(actor);

        Post post = actor.map(m -> Post.member(board, m, cmd.getTitle(), cmd.getContent()))
            .orElseGet(() -> Post.guest(board, cmd.getGuestNickname(), encoder.encode(cmd.getGuestPassword()), cmd.getTitle(), cmd.getContent()));
        
        postRepository.save(post);

        if (cmd.getAttachments() != null) {
            for (FileResponse f : cmd.getAttachments()) {
                attachmentRepository.save(Attachment.of(post, f.getOriginalName(), f.getStoredName(), f.getContentType(), f.getMediaKind(), f.getByteSize()));
            }
        }
        return post.getId();
    }

    @Transactional
    public void updatePost(Long id, PostUpdateCommand cmd, CustomUserDetails user) {
        Post post = postRepository.findById(id).filter(p -> p.getDeletedAt() == null).orElseThrow();
        post.checkEditable(actorOf(user), cmd.getGuestPassword(), encoder);
        post.update(cmd.getTitle(), cmd.getContent());
    }

    @Transactional
    public void deletePost(Long id, String guestPassword, CustomUserDetails user) {
        Post post = postRepository.findById(id).filter(p -> p.getDeletedAt() == null).orElseThrow();
        post.checkEditable(actorOf(user), guestPassword, encoder);
        post.softDelete();
    }

    @Transactional
    public void toggleLike(Long id, CustomUserDetails user) {
        if (user == null) throw new AuthenticationRequiredException("Login required");
        Post post = postRepository.findById(id).filter(p -> p.getDeletedAt() == null).orElseThrow();
        PostLikeId likeId = new PostLikeId(id, user.getId());
        if (likeRepository.existsById(likeId)) {
            likeRepository.deleteById(likeId);
            postRepository.decreaseLikeCount(id);
        } else {
            likeRepository.save(new PostLike(id, user.getId()));
            postRepository.increaseLikeCount(id);
        }
    }

    private Optional<Member> actorOf(CustomUserDetails user) {
        return Optional.ofNullable(user).map(CustomUserDetails::getMember);
    }
}
