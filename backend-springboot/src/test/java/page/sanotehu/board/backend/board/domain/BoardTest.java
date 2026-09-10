package page.sanotehu.board.backend.board.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardTest {

    private Board boardWithAttachmentsAllowed() throws Exception {
        Board board = new Board();
        setFieldValue(board, "allowsAttachment", true);
        setFieldValue(board, "name", "테스트 게시판");
        return board;
    }

    private Board boardWithAttachmentsDisallowed() throws Exception {
        Board board = new Board();
        setFieldValue(board, "allowsAttachment", false);
        setFieldValue(board, "name", "테스트 게시판");
        return board;
    }

    private void setFieldValue(Object object, String fieldName, Object value) throws Exception {
        Field field = Board.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(object, value);
    }

    @Test
    @DisplayName("첨부를 허용하는 게시판은 첨부가 있는 게시글을 허용한다")
    void allowsAttachmentWhenEnabled() throws Exception {
        Board board = boardWithAttachmentsAllowed();
        board.checkAttachmentAllowed(true);
    }

    @Test
    @DisplayName("첨부를 허용하지 않는 게시판에 첨부가 있으면 실패한다")
    void rejectsAttachmentWhenDisabled() throws Exception {
        Board board = boardWithAttachmentsDisallowed();
        assertThatThrownBy(() -> board.checkAttachmentAllowed(true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("첨부");
    }

    @Test
    @DisplayName("첨부를 허용하지 않는 게시판도 첨부가 없으면 허용한다")
    void allowsNoAttachmentWhenDisabled() throws Exception {
        Board board = boardWithAttachmentsDisallowed();
        board.checkAttachmentAllowed(false);
    }
}
