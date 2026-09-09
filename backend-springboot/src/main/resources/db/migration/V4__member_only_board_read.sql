-- V4__member_only_board_read.sql
-- PRD 1.2 / 10장 미결정 #1 결정: Q&A·자료실은 "회원제 게시판"이므로 읽기에도 로그인을 요구한다.
-- 자유게시판(free)은 비회원제로 유지한다.
UPDATE board
   SET requires_auth_to_read = TRUE
 WHERE slug IN ('qna', 'archive');
