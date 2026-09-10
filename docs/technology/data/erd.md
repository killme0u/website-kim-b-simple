# ERD

> 상태: `확인됨` — Flyway V1~V4 기준.
> 컬럼 상세는 [data-model.md](data-model.md) 참조.

## 전체 관계도

```
                         ┌─────────────────────────┐
                         │        member           │
                         │─────────────────────────│
                         │ PK id                   │
                         │ UQ username             │
                         │ UQ email                │
                         │ UQ nickname (부분)       │
                         │    password_hash        │
                         │    name, phone          │
                         │    status  (PENDING…)   │
                         │    role    (USER/ADMIN) │
                         │    must_change_password │
                         │    temp_password_…      │
                         │    created_at/updated_at│
                         └────┬───────────┬────────┘
                              │           │
              ┌───────────────┘           └──────────────┐
              │ 1:N                                 1:N  │
              ▼                                          ▼
  ┌───────────────────────┐              ┌───────────────────────┐
  │  verification_token   │              │       comment         │
  │───────────────────────│              │───────────────────────│
  │ PK id                 │              │ PK id                 │
  │ FK member_id  CASCADE │              │ FK post_id            │
  │ UQ token_hash (SHA256)│              │ FK member_id NOT NULL │
  │    purpose            │              │    content            │
  │    expires_at         │              │    created/updated_at │
  │    used_at            │              │    deleted_at         │
  └───────────────────────┘              └───────────▲───────────┘
                                                     │ 1:N
  ┌───────────────────────┐                          │
  │        board          │                          │
  │───────────────────────│              ┌───────────┴───────────┐
  │ PK id                 │              │         post          │
  │ UQ slug               │  1:N         │───────────────────────│
  │    name               ├─────────────►│ PK id                 │
  │    requires_auth_to_  │              │ FK board_id  NOT NULL │
  │      read / write     │              │ FK member_id NULLABLE │
  │    allows_comment     │              │    guest_nickname     │
  │    allows_attachment  │              │    guest_password_hash│
  │    display_order      │              │    title, content     │
  └───────────────────────┘              │    view_count         │
                                         │    like_count         │
                                         │    created/updated_at │
                                         │    deleted_at         │
                                         │ CK post_author_ck     │
                                         └──┬────────┬───────┬───┘
                                       1:N  │        │       │  1:N
                       ┌──────────────────  ┘        │       └──────────────┐
                       │                        1:N  │                      │
                       ▼                             ▼                      ▼
        ┌──────────────────────┐   ┌──────────────────────┐   ┌──────────────────────┐
        │      attachment      │   │      post_like       │   │    post_view_log     │
        │──────────────────────│   │──────────────────────│   │──────────────────────│
        │ PK id                │   │ PK post_id   ┐       │   │ PK post_id      ┐    │
        │ FK post_id  CASCADE  │   │ PK member_id ┘ 복합   │   │ PK member_id    │복합 │
        │ UQ stored_name       │   │    created_at        │   │ PK viewed_on    ┘    │
        │    original_name     │   │  (둘 다 CASCADE)      │   │    created_at        │
        │    content_type      │   └──────────────────────┘   │  (둘 다 CASCADE)      │
        │    media_kind        │                              └──────────────────────┘
        │    byte_size         │
        │    created_at        │
        └──────────────────────┘
```

`post_like`와 `post_view_log`는 `member`와도 FK 관계입니다
(그림에서는 선이 겹쳐 생략).

## 관계 요약 `확인됨`

| 부모 | 자식 | 카디널리티 | FK 옵션 | 필수 |
|---|---|---|---|---|
| `member` | `verification_token` | 1:N | `ON DELETE CASCADE` | 필수 |
| `member` | `post` | 1:N | (기본) | **선택** — 비회원 글 |
| `member` | `comment` | 1:N | (기본) | 필수 |
| `member` | `post_like` | 1:N | `ON DELETE CASCADE` | 필수 |
| `member` | `post_view_log` | 1:N | `ON DELETE CASCADE` | 필수 |
| `board` | `post` | 1:N | (기본) | 필수 |
| `post` | `comment` | 1:N | (기본) | 필수 |
| `post` | `attachment` | 1:N | `ON DELETE CASCADE` | 필수 |
| `post` | `post_like` | 1:N | `ON DELETE CASCADE` | 필수 |
| `post` | `post_view_log` | 1:N | `ON DELETE CASCADE` | 필수 |

## 이 그림에서 읽어야 할 세 가지

### 1. `post.member_id`가 nullable인 것이 전체 설계를 규정한다 `확인됨`

이 한 칸의 nullable 때문에:

- `post_author_ck` CHECK 제약이 필요합니다
- `Post.member()`와 `Post.guest()` 두 팩토리가 갈립니다
- `Post.checkEditable`이 로그인/비로그인 분기를 갖습니다
- `SecurityConfig`가 글 쓰기·수정·삭제를 `permitAll`로 둡니다
- `PostResponse.authorName`이 `username` 또는 `guestNickname`으로 갈립니다

**비회원 글 지원이 아키텍처 결정의 뿌리입니다.**

### 2. 복합 PK 두 개가 비즈니스 규칙이다 `확인됨`

```
post_like       PK (post_id, member_id)              한 사람당 한 번
post_view_log   PK (post_id, member_id, viewed_on)   하루에 한 번
```

애플리케이션에 중복 검사 코드가 없습니다.
`INSERT ... ON CONFLICT DO NOTHING`의 영향 행 수가 판정 결과입니다.

### 3. CASCADE가 소프트 삭제에서는 발동하지 않는다 `확인됨`

`attachment`, `post_like`, `post_view_log`가 `ON DELETE CASCADE`지만
글 삭제는 `deleted_at`을 세울 뿐 행을 지우지 않습니다.

**따라서 CASCADE는 실질적으로 죽어 있는 설정입니다.**
글을 삭제해도 첨부·좋아요·조회기록이 그대로 남습니다.
→ [data-retention.md](data-retention.md)

## 인덱스 `확인됨`

| 인덱스 | 테이블 | 컬럼 | 용도 |
|---|---|---|---|
| `idx_token_member` | `verification_token` | `(member_id, purpose)` | 회원별 토큰 조회 |
| `idx_post_list` | `post` | `(board_id, deleted_at, id DESC)` | 목록 조회 |
| `idx_comment_post` | `comment` | `(post_id, deleted_at, id)` | 댓글 목록 |
| `idx_attachment_post` | `attachment` | `(post_id)` | 글의 첨부 조회 |
| `idx_view_log_date` | `post_view_log` | `(viewed_on)` | 날짜 집계·정리용 |
| `ux_member_nickname` | `member` | `(nickname) WHERE NOT NULL` | 부분 UNIQUE |

UNIQUE 제약이 만드는 암묵적 인덱스: `member.username`, `member.email`,
`board.slug`, `attachment.stored_name`, `verification_token.token_hash`.

### 인덱스가 없어 아쉬운 곳 `제안`

| 쿼리 | 문제 |
|---|---|
| 키워드 검색 (`LIKE %kw%`) | 선행 `%` 때문에 인덱스 불가. 전체 스캔 |
| `findByMemberIdAndDeletedAtIsNullOrderByIdDesc` | `post`에 `(member_id, deleted_at, id)` 인덱스 없음 |
| `findByEmail` | UNIQUE라 인덱스 있음 — 문제 없음 |

마이페이지 글 목록은 `member_id`로 조회하는데 그 인덱스가 없습니다.
`idx_post_list`는 `board_id`가 선두라 쓸 수 없습니다.
회원 수가 늘고 글이 많아지면 느려집니다. `제안`

## 데이터 증가 특성 `제안`

| 테이블 | 증가 속도 | 상한 |
|---|---|---|
| `board` | 거의 없음 | 수동 관리 |
| `member` | 가입 수 | — |
| `post` | 글 수 | — |
| `comment` | 댓글 수 | — |
| `attachment` | 첨부 수 | — |
| `post_like` | 좋아요 수 | 회원 × 글 |
| `verification_token` | **가입 + 재발송 + 재설정 요청** | 정리 없음 |
| `post_view_log` | **회원 × 글 × 활동일수** | 정리 없음 |

`post_view_log`가 압도적으로 빠르게 자랍니다.
활동적인 회원 100명이 하루 20개 글을 보면 하루 2,000행, 연 73만 행입니다.

`verification_token`은 재발송 제한이 없어 악의적으로 부풀릴 수 있습니다.

둘 다 정리 정책이 없습니다. `미결정`

## 관련 문서

- [data-model.md](data-model.md) — 컬럼 상세
- [migration-policy.md](migration-policy.md) — 변경 규칙
- [../adr/ADR-002-postgresql.md](../adr/ADR-002-postgresql.md) — 복합 PK 결정 근거
- [../../features/state-machines.md](../../features/state-machines.md) — 상태 전이
