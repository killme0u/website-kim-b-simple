package page.sanotehu.board.backend.board.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import page.sanotehu.board.backend.board.adapter.in.web.dto.BoardResponse;
import page.sanotehu.board.backend.board.application.BoardService;

import java.util.List;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @GetMapping
    public List<BoardResponse> getAllBoards() {
        return boardService.getAllBoards();
    }

    @GetMapping("/{slug}")
    public BoardResponse getBoardBySlug(@PathVariable String slug) {
        return boardService.getBoardBySlug(slug);
    }
}