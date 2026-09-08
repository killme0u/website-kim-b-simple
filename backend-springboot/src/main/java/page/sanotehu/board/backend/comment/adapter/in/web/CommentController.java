package page.sanotehu.board.backend.comment.adapter.in.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import page.sanotehu.board.backend.comment.adapter.in.web.dto.CommentCommand;
import page.sanotehu.board.backend.comment.adapter.in.web.dto.CommentResponse;
import page.sanotehu.board.backend.comment.application.CommentService;
import page.sanotehu.board.backend.member.application.CustomUserDetails;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/posts/{postId}/comments")
    public List<CommentResponse> getComments(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return commentService.getComments(postId, user);
    }

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> createComment(
            @PathVariable Long postId,
            @RequestBody @Valid CommentCommand cmd,
            @AuthenticationPrincipal CustomUserDetails user) {
        return Map.of("id", commentService.createComment(postId, cmd, user));
    }

    @PutMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateComment(
            @PathVariable Long id,
            @RequestBody @Valid CommentCommand cmd,
            @AuthenticationPrincipal CustomUserDetails user) {
        commentService.updateComment(id, cmd, user);
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        commentService.deleteComment(id, user);
    }
}