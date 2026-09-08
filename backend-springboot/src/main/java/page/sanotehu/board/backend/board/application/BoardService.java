package page.sanotehu.board.backend.board.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.sanotehu.board.backend.board.adapter.in.web.dto.BoardResponse;
import page.sanotehu.board.backend.board.domain.BoardRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;

    @Transactional(readOnly = true)
    public List<BoardResponse> getAllBoards() {
        return boardRepository.findAll(Sort.by(Sort.Direction.ASC, "displayOrder")).stream()
                .map(BoardResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BoardResponse getBoardBySlug(String slug) {
        return boardRepository.findBySlug(slug)
                .map(BoardResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));
    }
}