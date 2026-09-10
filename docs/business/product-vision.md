# 제품 비전

> 상태 표기: 코드에서 확인한 것은 `확인됨`, 이 문서가 처음 제시하는 초안은 `제안`,
> 정해지지 않은 것은 `미결정`입니다. 표기 규칙은 [../README.md](../README.md) 참조.

## 한 문장

**하나의 배포 단위 안에서, 게시판마다 다른 참여 규칙을 데이터로 표현하는 커뮤니티 플랫폼.** `확인됨`

근거: `board` 테이블이 게시판별 정책을 컬럼으로 들고 있고(`requires_auth_to_read`,
`requires_auth_to_write`, `allows_comment`, `allows_attachment`),
도메인 객체 `Board`가 그 정책의 판정자입니다.
(`backend-springboot/src/main/java/page/sanotehu/board/backend/board/domain/Board.java:43-53`)

## 풀려는 문제

게시판 서비스는 보통 두 극단 중 하나로 갑니다.

- **전부 익명** — 진입 장벽이 없지만 책임 소재가 없고 스팸에 무방비
- **전부 회원제** — 관리는 쉽지만 한 번 물어보려는 사람을 잃음

이 제품은 그 선택을 **게시판 단위로 내리도록** 합니다. 자유게시판은 비회원도 읽고 쓰고,
Q&A와 자료실은 회원만 들어옵니다. 이 차이는 코드 분기가 아니라 `board` 테이블의 행 하나이므로,
새 정책을 가진 게시판을 추가하는 데 배포가 필요하지 않습니다. `확인됨`
(`backend-springboot/src/main/resources/db/migration/V2__seed_board.sql`,
`V4__member_only_board_read.sql`)

## 현재 게시판 구성 `확인됨`

| slug | 이름 | 읽기 로그인 | 쓰기 로그인 | 댓글 | 첨부 |
|---|---|---|---|---|---|
| `free` | 자유게시판 | 불필요 | 불필요 | 불가 | 불가 |
| `qna` | Q&A 게시판 | **필요** | 필요 | 가능 | 불가 |
| `archive` | 자료실 | **필요** | 필요 | 불가 | 가능 |

읽기 로그인 요구는 `V4__member_only_board_read.sql`이 `qna`·`archive`에 적용했습니다.
`PRD.md` 1.2의 회원제 게시판 정의를 따른 결정입니다(`todo.md` 2026-09-09 항목).

## 설계 원칙

### 1. 권한은 HTTP 레이어가 아니라 도메인이 판정한다 `확인됨`

`SecurityConfig`는 게시글 쓰기·수정·삭제를 `permitAll`로 열어 두고, 실제 판정은
`Board.checkWritable`과 `Post.checkEditable`이 합니다.
(`config/SecurityConfig.java:33-35`, `post/domain/Post.java:75-88`)

이렇게 한 이유는 비회원 글이 존재하기 때문입니다. 로그인 여부만으로는
비회원이 자기 글을 비밀번호로 수정하는 흐름을 표현할 수 없습니다.

대가도 분명합니다 — URL만 봐서는 누가 접근 가능한지 알 수 없습니다.
`todo.md`의 후속 검토 대상에 이 항목이 올라 있습니다. `미결정`

### 2. 중복 확인은 편의 기능이고, 방어선은 DB 제약이다 `확인됨`

아이디·닉네임 중복 확인 API는 UX를 위한 것이고, 실제로 중복을 막는 것은
UNIQUE 제약과 그것을 409로 바꾸는 예외 처리입니다.
(`PRD.md` 2.4, `common/GlobalExceptionHandler.java:16-20`)

여기서 나온 규칙: **조회와 저장은 같은 정규화를 공유해야 한다**(결정 D5).
`Member.normalizeNickname` 한 곳에만 규칙을 두는 이유입니다.
(`member/domain/Member.java:58-64`)

### 3. 복합 기본키가 곧 규칙이다 `확인됨`

조회수 중복 방지와 좋아요 중복 방지에 별도 검사 코드가 없습니다.
`post_view_log`의 PK가 `(post_id, member_id, viewed_on)`이고 `post_like`의 PK가
`(post_id, member_id)`이므로, 하루에 한 번과 한 사람당 한 번이 스키마 자체입니다.
`INSERT ... ON CONFLICT DO NOTHING`의 영향 행 수가 곧 판정 결과입니다.
(`post/domain/PostViewLogRepository.java:11-19`, `PRD.md` 2.6)

## 목표 사용자

| 유형 | 기대 행동 | 현재 지원 |
|---|---|---|
| 비회원 방문자 | 자유게시판 열람, 닉네임+비밀번호로 글 작성 | 지원 `확인됨` |
| 신규 회원 | 가입 → 이메일 인증 → Q&A·자료실 이용 | 지원 `확인됨` |
| 기존 회원 | 댓글, 좋아요, 마이페이지에서 내 활동 확인 | 지원 `확인됨` |
| 관리자 | 모든 글·댓글 수정·삭제 | 도메인 로직만 존재. 관리 화면 없음 `미결정` |

`MemberRole.ADMIN`은 `Post.checkEditable`과 `CommentService`에서 통과 조건으로 쓰이지만
(`post/domain/Post.java:78`, `comment/application/CommentService.java:64`),
관리자 계정을 만드는 경로도 관리자 전용 화면도 없습니다.

## 비목표 `확인됨`

아래는 현재 범위 밖입니다. 코드에 흔적이 없거나 의도적으로 제외되었습니다.

- **결제·주문** — 관련 도메인이 전혀 없습니다.
- **소셜 로그인** — `plan.md`가 OAuth 계약과 운영 키가 준비되지 않은 상태에서는 가짜 성공 UI를 제공하지 않는다고 명시하고, 구현하지 않았습니다.
- **실시간 알림** — WebSocket·SSE 의존성이 없습니다.
- **다국어** — 오류 메시지와 메일 템플릿이 한국어 고정입니다(`Locale.KOREA`, `member/application/SignupMailListener.java:76`).
- **파일 스토리지 외부화** — 로컬 파일시스템만 사용합니다(`app.storage.root`).

## 관련 문서

- [business-requirements.md](business-requirements.md) — 요구사항 목록
- [policy-rules.md](policy-rules.md) — 정책 규칙 상세
- [roadmap.md](roadmap.md) — 진행 상황
- [../technology/adr/ADR-001-modular-monolith.md](../technology/adr/ADR-001-modular-monolith.md) — 단일 배포 단위 결정
