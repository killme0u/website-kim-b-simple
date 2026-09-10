# 정책 규칙

> 상태: `확인됨` — 모든 규칙이 코드나 DB 제약에서 확인된 것입니다.
> 규칙마다 **강제 지점**(어디서 막히는가)을 함께 적습니다.

## 규칙을 읽는 법

이 시스템은 같은 규칙을 여러 층에서 막습니다. 어느 층이 진짜 방어선인지 구분하는 것이 중요합니다.

| 층 | 역할 | 우회 가능성 |
|---|---|---|
| 프론트엔드 | 사용자 안내 | 쉬움 — API 직접 호출 |
| 컨트롤러 검증 (`@Valid`) | 형식 검사 | 불가 |
| 도메인 메서드 | 권한 판정 | 불가 |
| DB 제약 | 최종 방어선 | 불가 |

**중복 확인 API는 프론트엔드 층에 속합니다.** 편의 기능이지 방어선이 아닙니다(`PRD.md` 2.4).

## PR-001 게시판 접근 정책

### PR-001-1 읽기 권한

게시판의 `requires_auth_to_read`가 참이면 비회원은 목록도 본문도 볼 수 없습니다.

- **강제 지점**: `Board.checkReadable` (`board/domain/Board.java:43-47`)
- **호출 위치**: `BoardService.getBoardBySlug`, `PostService.listPosts`, `PostService.getPost`, `CommentService.getComments`
- **실패 응답**: 401 `AUTH_REQUIRED`
- **현재 값**: `qna`, `archive`가 참 (`V4__member_only_board_read.sql`)

게시판 **목록**(`GET /api/boards`)은 이 검사를 하지 않습니다.
누구나 어떤 게시판이 있는지는 볼 수 있고, 입장 시점에 막힙니다.
(`board/application/BoardService.java:22-31`)

### PR-001-2 쓰기 권한

`requires_auth_to_write`가 참이면 비회원은 글을 쓸 수 없습니다.

- **강제 지점**: `Board.checkWritable` (`Board.java:49-53`)
- **호출 위치**: `PostService.createPost`, `CommentService.createComment`
- **실패 응답**: 401 `AUTH_REQUIRED`

### PR-001-3 댓글 허용

`allows_comment`가 거짓인 게시판에는 댓글을 달 수 없습니다.

- **강제 지점**: `CommentService.createComment` (`comment/application/CommentService.java:49-51`)
- **실패 응답**: 400 `BAD_REQUEST`
- **현재 값**: `qna`만 참

### PR-001-4 첨부 허용 `미결정`

`allows_attachment`는 **백엔드에서 강제되지 않습니다.**

- 프론트엔드가 첨부 UI 노출 판단에만 사용
- `PostService.createPost`가 `allowsAttachment`를 보지 않고 첨부를 저장 (`post/application/PostService.java:85-89`)
- API를 직접 호출하면 자유게시판에도 첨부를 붙일 수 있음

PR-001-3(댓글)은 막고 PR-001-4(첨부)는 막지 않는 비대칭입니다. 의도된 것인지 누락인지 불명확합니다.

## PR-002 계정 정책

### PR-002-1 가입 직후 상태

모든 신규 계정은 `PENDING`입니다.

- **강제 지점**: `Member.pending` (`member/domain/Member.java:75`)
- DB 기본값도 `'PENDING'` (`V1__init.sql`)

### PR-002-2 이메일 인증으로만 활성화

`PENDING` → `ACTIVE` 전환은 유효한 이메일 인증 토큰으로만 일어납니다.

- **강제 지점**: `Member.verifyEmail` (`Member.java:84-88`)
- 이미 `ACTIVE`이거나 다른 상태면 아무 일도 일어나지 않음 (조건문이 `PENDING`만 통과시킴)

### PR-002-3 인증 토큰 수명

| 용도 | 유효 기간 | 근거 |
|---|---|---|
| 이메일 인증 | 24시간 | `SignupService.java:42`, `VerificationService.java:78` |
| 비밀번호 재설정 | 1시간 | `VerificationService.java:85` |

- **1회용**: `used_at`이 설정되면 재사용 불가 (`VerificationToken.isUsable`, `member/domain/VerificationToken.java:54-58`)
- **원문 미저장**: SHA-256 해시만 DB에 저장

### PR-002-4 인증 메일 재발송 조건

`PENDING` 상태의 계정에만 재발송합니다.

- **강제 지점**: `VerificationService.resendEmailVerification` (`VerificationService.java:45-50`)
- 존재하지 않는 이메일이나 이미 `ACTIVE`인 계정에도 **202를 반환**합니다 (계정 열거 방지)

### PR-002-5 탈퇴 계정 제외

`DELETED` 상태는 아이디 찾기·비밀번호 재설정 대상에서 제외됩니다.

- **강제 지점**: `UsernameRecoveryService.java:21`, `VerificationService.java:59`

단, `DELETED`로 **전환하는 코드가 없습니다.** 이 필터는 현재 아무것도 걸러내지 않습니다. `미결정`

## PR-003 계정 열거 방지 `확인됨`

이메일이 가입되어 있는지를 응답으로 알 수 없게 합니다.

| API | 계정 유무와 무관하게 |
|---|---|
| `POST /api/members/password-reset/request` | 202 + `{"status":"requested"}` |
| `POST /api/members/verify-email/resend` | 202 + `{"status":"sent"}` |
| `POST /api/members/username-recovery` | 202 + 동일 안내 메시지 |

셋 다 `Optional.ifPresent`로 처리해 계정이 없으면 조용히 아무것도 하지 않습니다.
(`VerificationService.java:47-49, 58-60`, `UsernameRecoveryService.java:20-23`)

**예외**: 중복 확인 API는 의도적으로 존재 여부를 노출합니다.
`GET /api/members/username-availability`는 아이디가 쓰이는지 그대로 알려줍니다.
가입 UX를 위한 것이며, `PRD.md` 2.4가 이 트레이드오프를 명시적으로 받아들였습니다.

## PR-004 게시글 수정·삭제 권한 (결정 D1)

판정 순서가 중요합니다. `Post.checkEditable` (`post/domain/Post.java:75-88`)

```
로그인했는가?
├─ 예
│   ├─ 관리자인가? → 통과
│   ├─ 이 글의 작성자인가? → 통과
│   └─ 아니면 → 403 "작성자만 수정·삭제할 수 있습니다."
└─ 아니오
    ├─ 이 글이 회원 글인가? → 403 "회원이 작성한 글은 로그인 후..."
    └─ 비회원 글이면
        └─ 비밀번호가 맞는가? → 통과 / 403 "작성자 비밀번호가 일치하지 않습니다."
```

**핵심**: 로그인한 사용자는 비회원 글을 비밀번호로 수정할 수 없습니다.
로그인 분기에 들어가면 `member` 일치 여부만 봅니다.
로그인 상태에서 자기가 쓴 비회원 글을 고치려면 로그아웃해야 합니다. 의도된 동작인지 확인이 필요합니다. `미결정`

## PR-005 댓글 권한

- 작성: 로그인 필수 (`CommentService.java:44-46`)
- 수정·삭제: 작성자 본인 또는 관리자 (`CommentService.java:64-66, 76-78`)
- 비회원 댓글: 불가 (`comment.member_id NOT NULL`)

게시글과 달리 댓글에는 비회원 경로가 아예 없습니다.

## PR-006 조회수 정책 (결정 D2)

| 주체 | 규칙 | 강제 지점 |
|---|---|---|
| 회원 | 글당 하루 1회 | `post_view_log` PK `(post_id, member_id, viewed_on)` |
| 비회원 | **제한 없음** | 없음 `미결정` |

회원 경로는 `INSERT ... ON CONFLICT DO NOTHING`의 영향 행 수로 판정합니다.
1이면 처음 본 것이므로 조회수를 올리고, 0이면 이미 본 것이라 올리지 않습니다.
(`post/application/PostService.java:50-53`)

비회원 경로는 `PRD.md` 2.6이 누적 쿠키 방식을 설계했으나 구현되지 않았습니다.
현재 새로고침할 때마다 조회수가 오릅니다 (`PostService.java:54-56`).

증가는 항상 원자적 UPDATE입니다 — 읽고-더하고-쓰는 방식이 아닙니다.
(`post/domain/PostRepository.java:12-14`)

## PR-007 좋아요 정책

- 로그인 필수 (`PostService.java:109`)
- 회원당 글당 1회 — `post_like` PK `(post_id, member_id)`가 강제
- 토글 방식: 이미 눌렀으면 취소 (`PostService.java:112-118`)
- `like_count` 감소는 `like_count > 0` 조건부라 음수가 되지 않음 (`PostRepository.java:20-22`)

## PR-008 첨부파일 정책

| 규칙 | 값 | 강제 지점 |
|---|---|---|
| 최대 파일 크기 | 100MB | `application.yml` `spring.servlet.multipart.max-file-size` |
| 최대 요청 크기 | 100MB | 같은 파일 |
| 저장 파일명 | UUID + 원본 확장자 | `FileStorageService.java:50` |
| 경로 탈출 차단 | `rootPath` 밖이면 `SecurityException` | `FileStorageService.java:71-73` |
| SVG 처리 | `IMAGE`가 아니라 `FILE`로 분류 | `attachment/domain/MediaKind.java:7` |

**SVG를 `FILE`로 두는 이유**: SVG는 `<script>`를 품을 수 있어 인라인 렌더링하면 XSS 경로가 됩니다.
`FILE`로 분류하면 프론트엔드가 이미지 태그로 렌더링하지 않습니다. (`PRD.md` 2.5)

**허용 확장자 화이트리스트는 없습니다.** 어떤 파일이든 업로드됩니다. `미결정`

## PR-009 소프트 삭제

게시글과 댓글 모두 `deleted_at`을 세우고 행은 남깁니다.

- `Post.softDelete` (`post/domain/Post.java:95-97`)
- `Comment.softDelete` (`comment/application/CommentService.java:80`)
- 모든 조회가 `deleted_at IS NULL` 필터를 겁니다

첨부파일은 게시글이 소프트 삭제돼도 **디스크에 남습니다.**
`attachment` 행은 `ON DELETE CASCADE`지만 소프트 삭제는 행을 지우지 않으므로 발동하지 않습니다.
보존·정리 정책은 [../technology/data/data-retention.md](../technology/data/data-retention.md) 참조. `미결정`

## PR-010 비밀번호 정책

| 규칙 | 값 | 강제 지점 |
|---|---|---|
| 최소 길이 | 8자 | `SignupCommand`, `PasswordResetChangeCommand` `@Size(min=8)` |
| 최대 길이 | 100자 | 동일 |
| 저장 방식 | `DelegatingPasswordEncoder` (기본 bcrypt) | `config/SecurityConfig.java:70-73` |
| 복잡도 요구 | **없음** | — |

`aaaaaaaa`도 통과합니다. 복잡도·사전 공격 방어는 없습니다. `미결정`

## 관련 문서

- [../security/access-control.md](../security/access-control.md) — 권한 매트릭스
- [../features/error-policy.md](../features/error-policy.md) — 위반 시 응답
- [../features/state-machines.md](../features/state-machines.md) — 상태 전이 규칙
