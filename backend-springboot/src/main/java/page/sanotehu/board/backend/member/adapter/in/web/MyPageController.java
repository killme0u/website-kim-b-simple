package page.sanotehu.board.backend.member.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import page.sanotehu.board.backend.comment.adapter.in.web.dto.CommentResponse;
import page.sanotehu.board.backend.comment.domain.CommentRepository;
import page.sanotehu.board.backend.common.AuthenticationRequiredException;
import page.sanotehu.board.backend.member.application.CustomUserDetails;
import page.sanotehu.board.backend.post.adapter.in.web.dto.PostListItemResponse;
import page.sanotehu.board.backend.post.domain.PostRepository;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MyPageController {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @GetMapping("/posts")
    public Page<PostListItemResponse> getMyPosts(
            Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails user) {
        if (user == null) throw new AuthenticationRequiredException("Login required");
        return postRepository.findByMemberIdAndDeletedAtIsNullOrderByIdDesc(user.getId(), pageable)
                .map(PostListItemResponse::from);
    }

    @GetMapping("/comments")
    public Page<CommentResponse> getMyComments(
            Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails user) {
        if (user == null) throw new AuthenticationRequiredException("Login required");
        boolean isAdmin = user.getMember().getRole().name().equals("ADMIN");
        return commentRepository.findByMemberIdAndDeletedAtIsNullOrderByIdDesc(user.getId(), pageable)
                .map(c -> CommentResponse.from(c, user.getId(), isAdmin));
    }
}