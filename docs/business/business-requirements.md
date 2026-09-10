# 사업 요구사항

> 출처: `PRD.md` 1장(요구사항 정리), `plan.md`, 그리고 구현 코드.
> 각 항목의 구현 상태는 코드에서 확인한 것입니다.

## 읽는 법

| 열 | 뜻 |
|---|---|
| 상태 | `구현됨` / `부분` / `미구현` — 코드에서 확인 |
| 근거 | 해당 동작을 담고 있는 파일 |

백엔드 경로는 `backend-springboot/src/main/java/page/sanotehu/board/backend/` 기준,
프론트엔드는 `frontend-react/src/` 기준입니다.

## BR-001 회원 인증

| ID | 요구사항 | 상태 | 근거 |
|---|---|---|---|
| BR-001-1 | 아이디·비밀번호로 회원가입할 수 있다 | 구현됨 | `member/application/SignupService.java:28-47` |
| BR-001-2 | 가입 시 CAPTCHA를 **서버에서** 재검증한다 | 구현됨 | `SignupService.java:30-32`, `captcha/adapter/out/ConfiguredCaptchaVerifier.java` |
| BR-001-3 | 가입 직후 계정은 `PENDING`이며 이메일 인증 후 `ACTIVE`가 된다 | 구현됨 | `member/domain/Member.java:75, 84-88` |
| BR-001-4 | 이메일 인증 링크는 24시간 유효하고 1회만 쓸 수 있다 | 구현됨 | `SignupService.java:42`, `member/domain/VerificationToken.java:54-65` |
| BR-001-5 | 인증 메일을 재발송할 수 있다 | 구현됨 | `member/application/VerificationService.java:45-50` |
| BR-001-6 | 아이디·닉네임 중복을 가입 전에 확인할 수 있다 | 구현됨 | `member/adapter/in/web/MemberController.java:74-91` |
| BR-001-7 | 세션 쿠키 기반으로 로그인 상태를 유지한다 | 구현됨 | `config/SecurityConfig.java:43-47` |
| BR-001-8 | 아이디를 이메일로 찾을 수 있다 | 구현됨 | `member/application/UsernameRecoveryService.java` |
| BR-001-9 | 비밀번호를 이메일 링크로 재설정할 수 있다 | 구현됨 | `VerificationService.java:52-73` |
| BR-001-10 | 임시 비밀번호를 발급하고 만료·강제변경을 강제한다 | **미구현** | 컬럼과 `Member.changePassword`는 있으나 발급 경로가 없음 |
| BR-001-11 | 소셜(Google) 로그인 | **제공 안 함(최종 결정)** | `plan.md` 선결 결정 #3. 2026-09-10 확정. 재검토 없음 |

### BR-001-10 보충 `미결정`

`V1__init.sql`이 `must_change_password`와 `temp_password_expires_at`을 만들고
`MeResponse`가 `mustChangePassword`를 내려보내지만(`member/adapter/in/web/dto/MeResponse.java:18`),
임시 비밀번호를 **발급**하는 코드가 없습니다. 비밀번호 재설정은 토큰 링크 방식으로만 동작합니다.
`todo.md`의 후속 검토 대상에 같은 내용이 있습니다.

## BR-002 게시판

| ID | 요구사항 | 상태 | 근거 |
|---|---|---|---|
| BR-002-1 | 게시판마다 읽기·쓰기 로그인 요구를 다르게 설정한다 | 구현됨 | `board/domain/Board.java:43-53` |
| BR-002-2 | 게시판마다 댓글 허용 여부를 설정한다 | 구현됨 | `comment/application/CommentService.java:49-51` |
| BR-002-3 | 게시판마다 첨부 허용 여부를 설정한다 | **부분** | 컬럼과 필드는 있으나 `PostService.createPost`가 `allowsAttachment`를 검사하지 않음 |
| BR-002-4 | 게시판 목록은 누구나 볼 수 있다 | 구현됨 | `board/application/BoardService.java:26-31` |
| BR-002-5 | 게시판 정렬 순서를 지정한다 | 구현됨 | `BoardService.java:28` (`displayOrder` ASC) |

### BR-002-3 보충 `미결정`

`Board.allowsAttachment`는 필드로 존재하고 프론트엔드가 첨부 UI 노출 판단에 쓰지만,
백엔드 `PostService.createPost`는 `cmd.getAttachments()`를 게시판 정책 검사 없이 저장합니다.
(`post/application/PostService.java:85-89`)
API를 직접 호출하면 `allows_attachment = false`인 게시판에도 첨부를 붙일 수 있습니다.
댓글은 `CommentService`가 `allowsComment`를 검사하는데 첨부는 검사하지 않는 비대칭입니다.

## BR-003 게시글

| ID | 요구사항 | 상태 | 근거 |
|---|---|---|---|
| BR-003-1 | 회원 글과 비회원 글을 모두 지원한다 | 구현됨 | `post/domain/Post.java:56-73`, `V1__init.sql` `post_author_ck` |
| BR-003-2 | 비회원 글은 작성 시 받은 비밀번호로만 수정·삭제한다 | 구현됨 | `Post.java:82-88` |
| BR-003-3 | 회원 글은 작성자 본인이나 관리자만 수정·삭제한다 | 구현됨 | `Post.java:76-81` |
| BR-003-4 | 삭제는 소프트 삭제다 | 구현됨 | `Post.java:95-97` (`deletedAt`) |
| BR-003-5 | 목록은 페이지네이션한다 | 구현됨 | `post/adapter/in/web/PostController.java:23-30` (`Pageable`) |
| BR-003-6 | 제목·본문 키워드로 검색한다 | 구현됨 | `post/domain/PostRepository.java:24-26` |
| BR-003-7 | 본문은 마크다운으로 렌더링한다 | 구현됨 | `react-markdown` (`frontend-react/package.json`) |

## BR-004 조회수·좋아요

| ID | 요구사항 | 상태 | 근거 |
|---|---|---|---|
| BR-004-1 | 로그인 사용자의 조회수는 하루 1회만 증가한다 | 구현됨 | `PostService.java:50-53`, `post/domain/PostViewLogRepository.java:11-19` |
| BR-004-2 | 조회수 증가는 원자적 UPDATE다 | 구현됨 | `PostRepository.java:12-14` |
| BR-004-3 | 좋아요는 회원당 1회이며 토글된다 | 구현됨 | `PostService.java:107-119`, `post_like` 복합 PK |
| BR-004-4 | 좋아요는 로그인 필수다 | 구현됨 | `PostService.java:109` |
| BR-004-5 | 비로그인 조회수 중복 방지 | **미구현** | `PostService.java:54-56`이 무조건 증가 |

### BR-004-5 보충 `미결정`

`PRD.md` 2.6은 익명 사용자에 대해 **누적 쿠키** 방식을 설계했습니다(결정 D2, 하이브리드).
그러나 현재 `PostService.getPost`의 익명 분기는 쿠키를 보지 않고 매 요청 조회수를 올립니다.
설계와 구현이 갈라진 지점입니다. 새로고침만으로 조회수가 계속 오릅니다.

## BR-005 댓글

| ID | 요구사항 | 상태 | 근거 |
|---|---|---|---|
| BR-005-1 | 댓글은 로그인 회원만 작성한다 | 구현됨 | `CommentService.java:44-46` |
| BR-005-2 | 댓글 허용 게시판에서만 작성한다 | 구현됨 | `CommentService.java:49-51` |
| BR-005-3 | 작성자 본인이나 관리자만 수정·삭제한다 | 구현됨 | `CommentService.java:64-66, 76-78` |
| BR-005-4 | 삭제는 소프트 삭제다 | 구현됨 | `CommentService.java:80` |
| BR-005-5 | 비회원 댓글 | **미구현** | `comment.member_id`가 `NOT NULL`이라 스키마 수준에서 불가 |

## BR-006 첨부파일

| ID | 요구사항 | 상태 | 근거 |
|---|---|---|---|
| BR-006-1 | 파일을 업로드하고 다운로드한다 | 구현됨 | `attachment/adapter/in/web/FileController.java` |
| BR-006-2 | 저장 파일명은 추측 불가능해야 한다 | 구현됨 | UUID (`attachment/application/FileStorageService.java:50`) |
| BR-006-3 | 경로 탈출(path traversal)을 차단한다 | 구현됨 | `FileStorageService.java:71-73` |
| BR-006-4 | SVG는 이미지로 인라인 렌더링하지 않는다 | 구현됨 | `attachment/domain/MediaKind.java:7` |
| BR-006-5 | 다운로드/인라인을 파라미터로 구분한다 | 구현됨 | `FileController.java:43-47` |
| BR-006-6 | 업로드 크기를 제한한다 | 구현됨 | 100MB (`application.yml`) |
| BR-006-7 | 실제 내용 기반으로 MIME을 판정한다 | **부분** | 업로드는 클라이언트가 보낸 값을 신뢰. 다운로드만 서버 판정 |

### BR-006-7 보충 `미결정`

`PRD.md` 6.3의 업로드 보안 체크리스트(결정 D4)는 내용 기반 판정을 요구합니다.
`FileStorageService.storeFile`은 `file.getContentType()`(클라이언트 제공 값)을 그대로 저장하고,
그 값이 `MediaKind.from`으로 들어갑니다(`FileStorageService.java:58-66`).
업로드 시 `Content-Type: image/png`로 위장하면 `media_kind = IMAGE`로 기록됩니다.
다운로드 시점에는 `Files.probeContentType`으로 다시 판정하므로 브라우저에 내려가는
`Content-Type`은 서버 판정값이지만, DB에 남은 `media_kind`는 신뢰할 수 없습니다.
자세한 내용은 [../security/threat-model.md](../security/threat-model.md) T-006 참조.

## BR-007 운영·배포

| ID | 요구사항 | 상태 | 근거 |
|---|---|---|---|
| BR-007-1 | 단일 포트(8080)에서 SPA와 API를 함께 제공한다 | 구현됨 | `config/SpaResourceConfig.java` |
| BR-007-2 | Docker Compose 한 줄로 로컬 구동한다 | 구현됨 | `docker-compose.yml` |
| BR-007-3 | 비밀값을 저장소 밖(`.env`)에서 주입한다 | 구현됨 | `application.yml` `spring.config.import` |
| BR-007-4 | 작업 PC마다 다른 DB 주소를 코드 수정 없이 흡수한다 | 구현됨 | `PGSQL_HOST`/`PGSQL_PORT` |
| BR-007-5 | SMTP 미설정 시에도 가입 흐름이 막히지 않는다 | 구현됨 | `config/MailConfig.java:33-48` |
| BR-007-6 | 스키마 검증 실패가 DB 삭제로 이어지지 않는다 | 구현됨 | `flyway.clean-disabled: true` |
| BR-007-7 | CI에서 빌드·테스트를 자동 실행한다 | **미구현** | `.github`에 워크플로 파일 없음 |

## 관련 문서

- [policy-rules.md](policy-rules.md) — 위 요구사항이 만드는 운영 규칙
- [../features/feature-catalog.md](../features/feature-catalog.md) — 기능별 코드 위치
- [../features/acceptance-criteria.md](../features/acceptance-criteria.md) — 검증 기준
