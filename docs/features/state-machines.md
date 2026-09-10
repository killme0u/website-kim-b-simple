# 상태 기계

> 상태: `확인됨` — 모든 전이가 코드에 존재하는 것만 그렸습니다.
> **정의만 있고 전이 코드가 없는 상태**는 점선으로 표시했습니다.

## SM-001 회원 상태 (`MemberStatus`)

```
                    가입
                     │
                     ▼
              ┌─────────────┐
              │   PENDING   │  가입 직후 (Member.pending)
              └──────┬──────┘
                     │ verifyEmail()  ← 유효한 EMAIL_VERIFICATION 토큰
                     ▼
              ┌─────────────┐
              │   ACTIVE    │
              └─────────────┘
                     ┊
                     ┊ (전이 코드 없음)
                     ▼
              ┌─────────────┐
              │  SUSPENDED  │  enum에만 존재
              └─────────────┘
                     ┊
                     ┊ (전이 코드 없음)
                     ▼
              ┌─────────────┐
              │   DELETED   │  enum에만 존재
              └─────────────┘
```

### 전이 규칙

| 전이 | 트리거 | 코드 | 조건 |
|---|---|---|---|
| (없음) → `PENDING` | 회원가입 | `Member.pending` (`member/domain/Member.java:75`) | 항상 |
| `PENDING` → `ACTIVE` | 이메일 인증 | `Member.verifyEmail` (`Member.java:84-88`) | 현재 상태가 `PENDING`일 때만 |
| → `SUSPENDED` | — | **없음** `미결정` | — |
| → `DELETED` | — | **없음** `미결정` | — |

`verifyEmail()`은 `PENDING`이 아니면 조용히 아무 일도 하지 않습니다.
이미 `ACTIVE`인 계정에 유효한 토큰을 다시 써도 예외가 나지 않습니다
(토큰의 `markUsed()`가 재사용을 막으므로 실제로는 도달하기 어렵습니다).

### 상태별 허용 동작 `확인됨`

전이 코드는 없지만 **판정 코드는 있습니다.** 상태를 수동으로 바꾸면 즉시 동작합니다.

| 상태 | 로그인 | 근거 |
|---|---|---|
| `PENDING` | **가능** | `CustomUserDetails.isEnabled()` (`member/application/CustomUserDetails.java:57-59`) |
| `ACTIVE` | 가능 | 동일 |
| `SUSPENDED` | 불가 | `isAccountNonLocked()` (`CustomUserDetails.java:47-49`) |
| `DELETED` | 불가 | `isEnabled()`가 거짓 |

| 상태 | 비밀번호 재설정 | 아이디 찾기 |
|---|---|---|
| `PENDING` | 가능 | 가능 |
| `ACTIVE` | 가능 | 가능 |
| `SUSPENDED` | **가능** | **가능** |
| `DELETED` | 불가 | 불가 |

`SUSPENDED` 계정이 비밀번호를 재설정할 수 있는 것은
필터가 `status != DELETED`만 보기 때문입니다
(`member/application/VerificationService.java:59`, `UsernameRecoveryService.java:21`).
정지된 계정도 메일을 받습니다. `미결정`

### 인증 상태가 게시판 접근을 막지 않는다 `확인됨`

`Board.checkReadable`은 `actor.isEmpty()`(로그인 여부)만 봅니다.
`PENDING`으로 로그인하면 회원제 게시판에 들어갈 수 있습니다.
자세한 내용은 [user-journeys.md](user-journeys.md) J-002 참조.

## SM-002 인증 토큰 (`VerificationToken`)

```
        발급 (create)
           │
           │  expires_at = now + validFor
           │  used_at = NULL
           ▼
    ┌──────────────┐
    │   사용 가능    │  isUsable(purpose) == true
    └──────┬───────┘
           │
     ┌─────┴─────┐
     │           │
 markUsed()   시간 경과
     │           │
     ▼           ▼
┌─────────┐  ┌─────────┐
│  사용됨  │  │  만료됨  │
└─────────┘  └─────────┘
   둘 다 isUsable() == false, 되돌릴 수 없음
```

### `isUsable`의 세 조건 `확인됨`

세 조건을 **모두** 만족해야 사용 가능합니다 (`member/domain/VerificationToken.java:54-58`).

1. `purpose`가 기대값과 일치 — 이메일 인증 토큰으로 비밀번호를 바꿀 수 없음
2. `usedAt`이 `NULL` — 1회용
3. 현재 시각이 `expiresAt` 이전

### 용도별 수명 `확인됨`

| purpose | 유효 기간 | 발급 지점 |
|---|---|---|
| `EMAIL_VERIFICATION` | 24시간 | `SignupService.java:42`, `VerificationService.java:78` |
| `PASSWORD_RESET` | 1시간 | `VerificationService.java:85` |

### 저장되지 않는 것

DB에는 SHA-256 해시(`token_hash`)만 저장합니다. 원문 UUID는 메일 본문에만 존재합니다.
(`member/application/TokenHasher.java`, `SignupService.java:40-43`)

`markUsed()`를 이미 사용된 토큰에 호출하면 `IllegalArgumentException`이 납니다
(`VerificationToken.java:60-65`). 다만 호출 전에 `isUsable()`로 걸러지므로
정상 흐름에서는 도달하지 않는 이중 방어입니다.

### 만료 토큰 정리 `미결정`

만료되거나 사용된 토큰을 삭제하는 배치가 없습니다. `verification_token` 테이블은 계속 자랍니다.
→ [../technology/data/data-retention.md](../technology/data/data-retention.md)

## SM-003 게시글 (`Post`)

```
    작성 (guest 또는 member)
           │
           ▼
    ┌─────────────┐
    │   게시 중    │  deleted_at IS NULL
    └──────┬──────┘
           │ ▲
   update()│ │ (같은 상태 유지)
           │ │
           ▼ │
    ┌─────────────┐
    │   게시 중    │
    └──────┬──────┘
           │ softDelete()
           ▼
    ┌─────────────┐
    │   삭제됨     │  deleted_at = now
    └─────────────┘
       복구 경로 없음
```

### 작성 시점에 갈라지는 두 종류 `확인됨`

한 번 정해지면 바뀌지 않습니다. DB의 `post_author_ck` CHECK 제약이 강제합니다.

| 종류 | 생성자 | `member_id` | `guest_*` |
|---|---|---|---|
| 회원 글 | `Post.member(...)` | 있음 | `NULL` |
| 비회원 글 | `Post.guest(...)` | `NULL` | 있음 |

회원 글을 비회원 글로 바꾸거나 그 반대로 만드는 메서드가 없습니다.
`update()`는 `title`과 `content`만 바꿉니다 (`post/domain/Post.java:90-93`).

### 삭제 상태의 효과 `확인됨`

`deleted_at`이 설정되면 모든 조회에서 사라집니다.

- `PostRepository.search` — `p.deletedAt IS NULL` 조건
- `PostService.getPost` — `.filter(p -> p.getDeletedAt() == null)`
- `MyPageController` — `findByMemberIdAndDeletedAtIsNullOrderByIdDesc`

삭제된 글을 조회하면 `orElseThrow()`가 `NoSuchElementException`을 던집니다.
이 예외는 `GlobalExceptionHandler`에 핸들러가 없어 **500**이 됩니다.
→ [error-policy.md](error-policy.md) 미처리 예외 절 참조. `미결정`

### 남는 것

소프트 삭제이므로 다음이 그대로 남습니다.

| 대상 | 상태 |
|---|---|
| `post` 행 | 남음 (`deleted_at`만 설정) |
| `attachment` 행 | 남음 — `ON DELETE CASCADE`는 행 삭제 시에만 발동 |
| 디스크의 실제 파일 | 남음 |
| `post_like` 행 | 남음 |
| `post_view_log` 행 | 남음 |
| 하위 `comment` 행 | 남음 (`deleted_at` 설정되지 않음) |

**게시글을 삭제해도 댓글은 삭제 표시되지 않습니다.** 다만 댓글 조회가
게시글 존재를 먼저 확인하므로(`CommentService.getComments:30`) 화면에는 드러나지 않습니다.

## SM-004 댓글 (`Comment`)

게시글과 같은 구조입니다.

```
    작성 (회원만)
       │
       ▼
   ┌─────────┐  update()
   │ 게시 중  │◄──────────┐
   └────┬────┘           │
        │                │
        └────────────────┘
        │ softDelete()
        ▼
   ┌─────────┐
   │ 삭제됨   │  복구 없음
   └─────────┘
```

게시글과 다른 점: **비회원 경로가 없습니다.** `comment.member_id`가 `NOT NULL`입니다.

## SM-005 좋아요 (`PostLike`)

상태가 아니라 행의 존재 여부입니다.

```
   없음  ──POST /api/posts/{id}/like──►  있음
    ▲                                     │
    └──POST /api/posts/{id}/like──────────┘
              (같은 API가 토글)
```

| 전이 | 부수효과 |
|---|---|
| 없음 → 있음 | `post_like` INSERT + `like_count` +1 |
| 있음 → 없음 | `post_like` DELETE + `like_count` −1 (단, `like_count > 0`일 때만) |

(`post/application/PostService.java:107-119`, `post/domain/PostRepository.java:16-22`)

`like_count`는 `post_like` 행 수의 캐시입니다. 두 값이 어긋날 수 있는 구조지만
같은 트랜잭션에서 함께 갱신되므로 정상 경로에서는 일치합니다.

## SM-006 조회 기록 (`PostViewLog`)

전이가 아니라 **한 방향 기록**입니다.

```
  (post_id, member_id, viewed_on) 조합이
     없으면 → INSERT 성공 (1행) → view_count +1
     있으면 → ON CONFLICT DO NOTHING (0행) → 증가 없음
```

날짜가 바뀌면 새 조합이므로 다시 1회 증가합니다.
행을 삭제하는 코드는 없습니다.

비회원에게는 이 기계가 적용되지 않습니다 — 무조건 증가합니다
(`PostService.java:54-56`). `미결정`

## 관련 문서

- [../business/policy-rules.md](../business/policy-rules.md) — 각 전이의 정책 근거
- [error-policy.md](error-policy.md) — 전이 실패 시 응답
- [../technology/data/data-model.md](../technology/data/data-model.md) — 상태 컬럼 정의
