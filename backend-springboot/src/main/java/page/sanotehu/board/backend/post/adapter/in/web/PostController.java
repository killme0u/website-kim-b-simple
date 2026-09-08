package page.sanotehu.board.backend.post.adapter.in.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import page.sanotehu.board.backend.member.application.CustomUserDetails;
import page.sanotehu.board.backend.post.adapter.in.web.dto.*;
import page.sanotehu.board.backend.post.application.PostService;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping("/boards/{boardSlug}/posts")
    public Page<PostListItemResponse> listPosts(
            @PathVariable String boardSlug,
            @RequestParam(required = false) String keyword,
            Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails user) {
        return postService.listPosts(boardSlug, keyword, pageable, user);
    }

    @PostMapping("/boards/{boardSlug}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createPost(
            @PathVariable String boardSlug,
            @RequestBody @Valid PostCommand cmd,
            @AuthenticationPrincipal CustomUserDetails user) {
        return Map.of("id", postService.createPost(boardSlug, cmd, user));
    }

    @GetMapping("/posts/{id}")
    public PostResponse getPost(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        return postService.getPost(id, user);
    }

    @PutMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePost(
            @PathVariable Long id,
            @RequestBody @Valid PostUpdateCommand cmd,
            @AuthenticationPrincipal CustomUserDetails user) {
        postService.updatePost(id, cmd, user);
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(
            @PathVariable Long id,
            @RequestParam(required = false) String guestPassword,
            @AuthenticationPrincipal CustomUserDetails user) {
        postService.deletePost(id, guestPassword, user);
    }

    @PostMapping("/posts/{id}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void toggleLike(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        postService.toggleLike(id, user);
    }
}