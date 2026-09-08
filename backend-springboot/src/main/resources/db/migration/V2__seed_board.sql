-- V2__seed_board.sql
INSERT INTO board (slug, name, requires_auth_to_read, requires_auth_to_write,
                   allows_comment, allows_attachment, display_order)
VALUES
  ('free',    '자유게시판', false, false, false, false, 1),
  ('qna',     'Q&A 게시판', false, true,  true,  false, 2),
  ('archive', '자료실',     false, true,  false, true,  3);