# 기능 카탈로그

> 상태: `확인됨` — 모든 항목이 코드에 존재합니다.
> 경로는 백엔드 `backend-springboot/src/main/java/page/sanotehu/board/backend/`,
> 프론트엔드 `frontend-react/src/` 기준입니다.

## 한눈에 보기

| 영역 | 기능 수 | 완전 구현 | 부분 | 설계만 |
|---|---|---|---|---|
| 인증·회원 | 9 | 8 | 0 | 1 |
| 게시판 | 5 | 4 | 1 | 0 |
| 게시글 | 8 | 8 | 0 | 0 |
| 댓글 | 4 | 4 | 0 | 0 |
| 첨부파일 | 5 | 4 | 1 | 0 |
| 반응(조회수·좋아요) | 4 | 3 | 0 | 1 |
| 화면 | 11 | 11 | 0 | 0 |

## F-100 인증·회원

| ID | 기능 | 진입점 | 도메인·서비스 | 화면 |
|---|---|---|---|---|
| F-101 | 회원가입 | `POST /api/members/signup` | `SignupService` | `SignupPage.tsx` |
| F-102 | CAPTCHA 서버 검증 | 가입·비밀번호재설정에 내장 | `ConfiguredCaptchaVerifier` | `CaptchaField.tsx` |
| F-103 | 이메일 인증 | `GET /api/members/verify-email` | `VerificationService.verifyEmail` | `VerifyEmailPage.tsx` |
| F-104 | 인증 메일 재발송 | `POST /api/members/verify-email/resend` | `VerificationService.resendEmailVerification` | `SignupPage.tsx` |
| F-105 | 로그인 | `POST /api/auth/login` | Spring Security `formLogin` | `LoginPage.tsx` |
| F-106 | 로그아웃 | `POST /api/auth/logout` | Spring Security `logout` | `RootLayout.tsx` |
| F-107 | 아이디 중복 확인 | `GET /api/members/username-availability` | `MemberRepository.findByUsername` | `SignupPage.tsx` |
| F-108 | 닉네임 중복 확인 | `GET /api/members/nickname-availability` | `Member.normalizeNickname` + 조회 | `SignupPage.tsx` |
| F-109 | 아이디 찾기 | `POST /api/members/username-recovery` | `UsernameRecoveryService` | `FindUsernamePage.tsx` |
| F-110 | 비밀번호 재설정 요청 | `POST /api/members/password-reset/request` | `VerificationService.requestPasswordReset` | `FindPasswordPage.tsx` |
| F-111 | 비밀번호 변경 | `POST /api/members/password-reset/change` | `VerificationService.changePassword` | `FindPasswordPage.tsx` |
| F-112 | 현재 사용자 조회 | `GET /api/me` | `AuthController` | `lib/session.ts` |
| F-113 | 임시 비밀번호 발급 | — | **없음** | — |

### F-102 CAPTCHA 상세 `확인됨`

두 곳에서 검증합니다: 회원가입(`SignupService.java:30`)과
비밀번호 재설정 요청(`VerificationService.java:54`).
로그인에는 CAPTCHA가 없습니다.

동작 모드 (`captcha/adapter/out/ConfiguredCaptchaVerifier.java`)

| 모드 | 동작 | 용도 |
|---|---|---|
| `fake` (기본값) | `CAPTCHA_EXPECTED_TOKEN`과 문자열 일치만 확인 | 개발 |
| `remote` | provider 엔드포인트에 POST 후 `success` 필드 확인 | 운영 |
| 그 외 / 설정 누락 | 항상 `false` | 안전 실패 |

provider 호출 실패·타임아웃·예외는 모두 `false`로 흡수됩니다(`ConfiguredCaptchaVerifier.java:60-67`).
즉 **provider 장애 시 가입이 전면 차단**됩니다. 실패를 열어두지 않는 선택입니다.

### F-108 닉네임 중복 확인의 정규화 `확인됨`

조회와 저장이 `Member.normalizeNickname` 한 곳을 공유합니다(결정 D5).

```
정규화: null → null,  앞뒤 공백 제거,  빈 문자열 → null
```

정규화 결과가 `null`이면 닉네임을 쓰지 않겠다는 뜻이므로 충돌 대상이 없어 `available: true`입니다.
(`member/adapter/in/web/MemberController.java:86-91`, `member/domain/Member.java:58-64`)

이 규칙이 갈라졌던 것이 `5cca82f` 커밋에서 고친 버그입니다.
조회는 원본으로, 저장은 `trim` 후 하던 탓에 앞에 공백이 붙은 닉네임이
"사용 가능"으로 보인 뒤 가입에서 409가 났습니다.

## F-200 게시판

| ID | 기능 | 진입점 | 판정 위치 |
|---|---|---|---|
| F-201 | 게시판 목록 조회 | `GET /api/boards` | 권한 검사 없음 |
| F-202 | 게시판 단건 조회 | `GET /api/boards/{slug}` | `Board.checkReadable` |
| F-203 | 읽기 권한 정책 | 전 조회 API | `Board.checkReadable` |
| F-204 | 쓰기 권한 정책 | 글·댓글 작성 | `Board.checkWritable` |
| F-205 | 첨부 허용 정책 | `POST /api/boards/{slug}/posts` | `Board.checkAttachmentAllowed` |

## F-300 게시글

| ID | 기능 | 진입점 | 비고 |
|---|---|---|---|
| F-301 | 목록 조회 (페이징) | `GET /api/boards/{slug}/posts` | `Pageable` 표준 파라미터 |
| F-302 | 키워드 검색 | 같은 API `?keyword=` | 제목·본문 `LIKE` |
| F-303 | 단건 조회 | `GET /api/posts/{id}` | 조회수 증가 부수효과 있음 |
| F-304 | 회원 글 작성 | `POST /api/boards/{slug}/posts` | 세션 있으면 자동 |
| F-305 | 비회원 글 작성 | 같은 API | `guestNickname` + `guestPassword` |
| F-306 | 수정 | `PUT /api/posts/{id}` | `Post.checkEditable` |
| F-307 | 삭제 (소프트) | `DELETE /api/posts/{id}` | `deleted_at` 설정 |
| F-308 | 마크다운 렌더링 | 프론트엔드 | `react-markdown` |

### F-303의 부수효과 `확인됨`

`GET /api/posts/{id}`는 읽기 API지만 `@Transactional`(읽기 전용 아님)이며
조회수를 증가시킵니다(`post/application/PostService.java:44-56`).
GET이 상태를 바꾸는 구조라 캐시·프리페치·크롤러에 취약합니다.
→ [../technology/api/api-guidelines.md](../technology/api/api-guidelines.md)

## F-400 댓글

| ID | 기능 | 진입점 | 제약 |
|---|---|---|---|
| F-401 | 목록 조회 | `GET /api/posts/{postId}/comments` | 게시판 읽기 권한 검사 |
| F-402 | 작성 | `POST /api/posts/{postId}/comments` | 로그인 + `allowsComment` |
| F-403 | 수정 | `PUT /api/comments/{id}` | 작성자 또는 관리자 |
| F-404 | 삭제 (소프트) | `DELETE /api/comments/{id}` | 작성자 또는 관리자 |

댓글 목록은 페이징하지 않습니다 — 전체를 한 번에 반환합니다
(`comment/application/CommentService.java:37-39`).

## F-500 첨부파일

| ID | 기능 | 진입점 | 비고 |
|---|---|---|---|
| F-501 | 업로드 | `POST /api/files` | multipart, 100MB 제한 |
| F-502 | 다운로드·인라인 | `GET /api/files/{storedName}` | `?download=` 유무로 구분 |
| F-503 | UUID 저장명 | `FileStorageService.storeFile` | 원본 확장자 유지 |
| F-504 | 경로 탈출 차단 | `FileStorageService.loadFileAsResource` | `SecurityException` |
| F-505 | 미디어 종류 판정 | `MediaKind.from` | SVG는 `FILE`로 강등 |

업로드와 게시글 작성이 분리되어 있습니다. 먼저 `/api/files`로 올려 `FileResponse`를 받고,
그것을 `PostCommand.attachments`에 담아 글을 저장합니다(`post/application/PostService.java:85-89`).

**고아 파일 문제** `미결정`: 업로드 후 글을 저장하지 않으면 디스크에 파일만 남고
`attachment` 행은 생기지 않습니다. 정리 작업이 없습니다.

## F-600 반응

| ID | 기능 | 규칙 | 상태 |
|---|---|---|---|
| F-601 | 회원 조회수 중복 방지 | 하루 1회 | 구현됨 |
| F-602 | 비회원 조회수 중복 방지 | 누적 쿠키 (설계) | **미구현** `미결정` |
| F-603 | 좋아요 토글 | 회원당 1회 | 구현됨 |
| F-604 | 좋아요 낙관적 업데이트 | 프론트엔드 | `PostPage.tsx` |

## F-700 화면 `확인됨`

`frontend-react/src/App.tsx`의 라우트 정의 기준입니다.

| 경로 | 컴포넌트 | 로그인 필요 |
|---|---|---|
| `/` | `HomePage` | 아니오 |
| `/login` | `LoginPage` | 아니오 |
| `/signup` | `SignupPage` | 아니오 |
| `/verify-email` | `VerifyEmailPage` | 아니오 (토큰으로 진입) |
| `/find-username` | `FindUsernamePage` | 아니오 |
| `/find-password` | `FindPasswordPage` | 아니오 |
| `/me` | `MyPage` | 예 |
| `/boards/:slug` | `BoardPage` | 게시판 정책에 따름 |
| `/boards/:slug/posts/new` | `PostEditPage` | 게시판 정책에 따름 |
| `/posts/:id` | `PostPage` | 게시판 정책에 따름 |
| `/posts/:id/edit` | `PostEditPage` | 글 소유에 따름 |

라우트 자체에는 가드가 없습니다. API가 401을 주면
`api.interceptors`가 `authStore.logout()`을 호출하고 화면이 반응합니다
(`lib/axios.ts:26-34`). 서버 판정을 단일 진실 원천으로 삼는 구조입니다.

## 마이페이지

| ID | 기능 | 진입점 |
|---|---|---|
| F-711 | 내 게시글 | `GET /api/me/posts` |
| F-712 | 내 댓글 | `GET /api/me/comments` |

둘 다 `Pageable`을 받고 삭제되지 않은 것만 최신순으로 반환합니다
(`member/adapter/in/web/MyPageController.java`).

## 관련 문서

- [api-behavior.md](api-behavior.md) — 엔드포인트별 상세 동작
- [user-journeys.md](user-journeys.md) — 기능을 잇는 사용자 흐름
- [../business/business-requirements.md](../business/business-requirements.md) — 요구사항 대응
