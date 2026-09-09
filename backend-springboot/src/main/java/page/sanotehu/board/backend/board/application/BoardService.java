package page.sanotehu.board.backend.board.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.sanotehu.board.backend.board.adapter.in.web.dto.BoardResponse;
import page.sanotehu.board.backend.board.domain.Board;
import page.sanotehu.board.backend.board.domain.BoardRepository;
import page.sanotehu.board.backend.member.application.CustomUserDetails;
import page.sanotehu.board.backend.member.domain.Member;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;

    /**
     * 게시판 목록은 누구나 볼 수 있다. 회원제 게시판은 requiresAuthToRead 플래그로 구분되며
     * 실제 입장 여부는 {@link #getBoardBySlug} 및 게시글 조회 시점에 판정한다.
     */
    @Transactional(readOnly = true)
    public List<BoardResponse> getAllBoards() {
        return boardRepository.findAll(Sort.by(Sort.Direction.ASC, "displayOrder")).stream()
                .map(BoardResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BoardResponse getBoardBySlug(String slug, CustomUserDetails user) {
        Board board = boardRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시판입니다: " + slug));
        board.checkReadable(actorOf(user));
        return BoardResponse.from(board);
    }

    private Optional<Member> actorOf(CustomUserDetails user) {
        return Optional.ofNullable(user).map(CustomUserDetails::getMember);
    }
}
