# 데이터 모델

> 상태: `확인됨` — Flyway V1~V4와 JPA 엔티티에서 도출.
> 스키마의 유일한 출처는 마이그레이션입니다(`ddl-auto: validate`).

## 테이블 목록 `확인됨`

| 테이블 | 역할 | 행 증가 요인 |
|---|---|---|
| `member` | 회원 | 가입 |
| `verification_token` | 이메일 인증·비밀번호 재설정 토큰 | 가입, 재발송, 재설정 요청 |
| `board` | 게시판과 그 정책 | 수동 (마이그레이션) |
| `post` | 게시글 (회원/비회원) | 글 작성 |
| `comment` | 댓글 (회원만) | 댓글 작성 |
| `attachment` | 첨부파일 메타데이터 | 첨부 있는 글 작성 |
| `post_like` | 좋아요 | 좋아요 |
| `post_view_log` | 회원 조회 기록 | 회원의 글 조회 (하루 1회) |

## member `확인됨`

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `username` | `VARCHAR(30)` | NOT NULL, **UNIQUE** | 로그인 아이디. **화면에 작성자명으로 노출됨** |
| `nickname` | `VARCHAR(30)` | 부분 UNIQUE | V3에서 추가. 선택 항목 |
| `password_hash` | `VARCHAR(100)` | NOT NULL | bcrypt (`{bcrypt}` 접두사 포함) |
| `name` | `VARCHAR(50)` | NOT NULL | 실명 |
| `email` | `VARCHAR(255)` | NOT NULL, **UNIQUE** | 인증·복구용 |
| `phone` | `VARCHAR(20)` | **NOT NULL** | **사용처 없음** `미결정` |
| `status` | `VARCHAR(20)` | NOT NULL, 기본 `'PENDING'` | `MemberStatus` |
| `role` | `VARCHAR(20)` | NOT NULL, 기본 `'USER'` | `MemberRole` |
| `must_change_password` | `BOOLEAN` | NOT NULL, 기본 `FALSE` | **미사용** `미결정` |
| `temp_password_expires_at` | `TIMESTAMPTZ` | | **미사용** `미결정` |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, 기본 `now()` | |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, 기본 `now()` | JPA Auditing |

### 부분 UNIQUE 인덱스 `확인됨`

```sql
CREATE UNIQUE INDEX ux_member_nickname
    ON member (nickname)
    WHERE nickname IS NOT NULL;
```

닉네임이 선택 항목이라 여러 회원이 `NULL`일 수 있어야 하지만,
값이 있으면 유일해야 합니다. `WHERE` 절이 그 의도를 명시합니다.

이것이 `Member.normalizeNickname`이 빈 문자열을 `null`로 바꾸는 이유입니다.
`""`가 여럿 들어오면 UNIQUE 위반이 되지만 `NULL`은 괜찮습니다.

### 미사용 컬럼 3개 `미결정`

| 컬럼 | 상태 |
|---|---|
| `phone` | 필수 수집인데 읽는 코드가 없음 |
| `must_change_password` | `MeResponse`에 실려 나가지만 항상 `false` (설정하는 코드 없음) |
| `temp_password_expires_at` | 설정·검사하는 코드 없음 |

뒤의 둘은 임시 비밀번호 정책용인데 발급 경로가 구현되지 않았습니다.
`todo.md`에 후속 검토로 등록되어 있습니다.

## verification_token `확인됨`

| 컬럼 | 타입 | 제약 |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `member_id` | `BIGINT` | NOT NULL, FK → `member(id)` **ON DELETE CASCADE** |
| `token_hash` | `VARCHAR(64)` | NOT NULL, **UNIQUE** |
| `purpose` | `VARCHAR(30)` | NOT NULL |
| `expires_at` | `TIMESTAMPTZ` | NOT NULL |
| `used_at` | `TIMESTAMPTZ` | |

인덱스: `idx_token_member (member_id, purpose)`

| 사실 | 의미 |
|---|---|
| `token_hash`가 SHA-256 hex (64자) | **원문을 저장하지 않음** |
| `purpose` 값 | `EMAIL_VERIFICATION`, `PASSWORD_RESET` (코드 상수) |
| `used_at`이 `NULL`이 아니면 | 사용된 토큰 |

**정리 배치가 없습니다.** 만료·사용된 토큰이 계속 쌓입니다.
재발송에 횟수 제한이 없어 같은 회원의 유효 토큰이 여럿 공존할 수도 있습니다. `미결정`

`purpose`가 `VARCHAR`이고 enum이 아니라 DB 수준 제약이 없습니다.

## board `확인됨`

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | API에 노출되지 않음 |
| `slug` | `VARCHAR(30)` | NOT NULL, **UNIQUE** | 외부 식별자 |
| `name` | `VARCHAR(50)` | NOT NULL | 표시명 |
| `requires_auth_to_read` | `BOOLEAN` | NOT NULL, 기본 `FALSE` | V1에서 추가 |
| `requires_auth_to_write` | `BOOLEAN` | NOT NULL | |
| `allows_comment` | `BOOLEAN` | NOT NULL | |
| `allows_attachment` | `BOOLEAN` | NOT NULL | **백엔드 미검증** `미결정` |
| `display_order` | `INT` | NOT NULL, 기본 `0` | 정렬 키 |

### 현재 데이터 `확인됨`

V2 시드 + V4 갱신의 결과입니다.

| slug | name | read | write | comment | attachment | order |
|---|---|---|---|---|---|---|
| `free` | 자유게시판 | `false` | `false` | `false` | `false` | 1 |
| `qna` | Q&A 게시판 | **`true`** | `true` | `true` | `false` | 2 |
| `archive` | 자료실 | **`true`** | `true` | `false` | `true` | 3 |

`requires_auth_to_read`는 V2에서 전부 `false`로 시드된 뒤
V4가 `qna`·`archive`를 `true`로 바꿨습니다.

**V2를 읽고 현재 상태를 판단하면 틀립니다.** 반드시 V4까지 봐야 합니다.

## post `확인됨`

| 컬럼 | 타입 | 제약 |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `board_id` | `BIGINT` | NOT NULL, FK → `board(id)` |
| `member_id` | `BIGINT` | FK → `member(id)` (nullable) |
| `guest_nickname` | `VARCHAR(30)` | nullable |
| `guest_password_hash` | `VARCHAR(100)` | nullable |
| `title` | `VARCHAR(200)` | NOT NULL |
| `content` | `TEXT` | NOT NULL |
| `view_count` | `INT` | NOT NULL, 기본 `0` |
| `like_count` | `INT` | NOT NULL, 기본 `0` |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, 기본 `now()` |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, 기본 `now()` |
| `deleted_at` | `TIMESTAMPTZ` | 소프트 삭제 |

### CHECK 제약 — 회원 글과 비회원 글의 배타성 `확인됨`

```sql
CONSTRAINT post_author_ck CHECK (
    (member_id IS NOT NULL AND guest_nickname IS NULL AND guest_password_hash IS NULL)
    OR
    (member_id IS NULL AND guest_nickname IS NOT NULL AND guest_password_hash IS NOT NULL)
)
```

둘 다 있거나 둘 다 없는 상태를 DB가 거부합니다.

**부작용**: 비로그인 사용자가 `guestNickname` 없이 글을 쓰면
이 제약 위반이 `DataIntegrityViolationException`으로 잡혀
**409 `DUPLICATE`("이미 사용 중인 값이거나 중복된 데이터입니다")** 가 됩니다.
중복이 아닌데 중복이라고 말합니다. `미결정`

### 인덱스 `확인됨`

```sql
CREATE INDEX idx_post_list ON post (board_id, deleted_at, id DESC);
```

목록 조회 쿼리(`board_id` + `deleted_at IS NULL` + `id` 정렬)에 맞춘 복합 인덱스입니다.

**키워드 검색에는 쓰이지 않습니다.**

```sql
p.title LIKE %:keyword% OR p.content LIKE %:keyword%
```

앞에 `%`가 붙는 LIKE라 인덱스를 탈 수 없습니다. 전체 스캔입니다.
글이 많아지면 느려집니다. PostgreSQL의 `pg_trgm` GIN 인덱스나
전문 검색(`tsvector`)이 대안입니다. `제안`

### 카운터 컬럼의 성격 `확인됨`

`view_count`와 `like_count`는 집계의 **캐시**입니다.

| 컬럼 | 진실의 출처 | 갱신 방식 |
|---|---|---|
| `view_count` | (없음 — 비회원 조회는 로그가 없음) | 원자적 UPDATE |
| `like_count` | `post_like` 행 수 | 원자적 UPDATE |

`like_count`는 `post_like`에서 다시 계산할 수 있지만
`view_count`는 복원할 수 없습니다. 비회원 조회 기록이 남지 않기 때문입니다.

## comment `확인됨`

| 컬럼 | 타입 | 제약 |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `post_id` | `BIGINT` | NOT NULL, FK → `post(id)` |
| `member_id` | `BIGINT` | **NOT NULL**, FK → `member(id)` |
| `content` | `TEXT` | NOT NULL |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, 기본 `now()` |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, 기본 `now()` |
| `deleted_at` | `TIMESTAMPTZ` | 소프트 삭제 |

인덱스: `idx_comment_post (post_id, deleted_at, id)`

**`member_id`가 `NOT NULL`이라 비회원 댓글이 스키마 수준에서 불가능합니다.**
게시글과 달리 게스트 컬럼이 아예 없습니다.

**계층 구조(대댓글)가 없습니다.** `parent_id`가 없어 평면 목록입니다.

`post_id` FK에 `ON DELETE CASCADE`가 없습니다 — 소프트 삭제를 쓰므로
실제로 발동할 일이 없지만, 하드 삭제를 하려면 문제가 됩니다.

## attachment `확인됨`

| 컬럼 | 타입 | 제약 |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `post_id` | `BIGINT` | NOT NULL, FK → `post(id)` **ON DELETE CASCADE** |
| `original_name` | `VARCHAR(255)` | NOT NULL |
| `stored_name` | `VARCHAR(100)` | NOT NULL, **UNIQUE** |
| `content_type` | `VARCHAR(100)` | NOT NULL |
| `media_kind` | `VARCHAR(10)` | NOT NULL |
| `byte_size` | `BIGINT` | NOT NULL |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, 기본 `now()` |

인덱스: `idx_attachment_post (post_id)`

| 사실 | 의미 |
|---|---|
| `stored_name`이 UNIQUE | UUID 충돌 방어 |
| `content_type`이 **클라이언트 제공 값** | 신뢰할 수 없음 `미결정` |
| `media_kind` 값 | `IMAGE`, `VIDEO`, `AUDIO`, `FILE` |
| `ON DELETE CASCADE` | **소프트 삭제에서는 발동하지 않음** |

**디스크 파일과 이 테이블은 동기화되지 않습니다.**

| 상황 | 결과 |
|---|---|
| 업로드 후 글 저장 안 함 | 파일만 남고 행은 없음 (고아 파일) |
| 글 소프트 삭제 | 행도 파일도 그대로 남음 |
| 행을 하드 삭제 | 파일은 디스크에 남음 |

→ [data-retention.md](data-retention.md)

## post_like `확인됨`

```sql
CREATE TABLE post_like (
    post_id    BIGINT NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    member_id  BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, member_id)
);
```

**PK가 곧 규칙입니다** — 한 회원은 한 글에 한 번만.
별도 중복 검사 코드가 없습니다.

## post_view_log `확인됨`

```sql
CREATE TABLE post_view_log (
    post_id    BIGINT NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    member_id  BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    viewed_on  DATE   NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, member_id, viewed_on)
);
CREATE INDEX idx_view_log_date ON post_view_log (viewed_on);
```

**PK가 곧 "하루에 한 번" 규칙입니다.**

| 컬럼 | 주의 |
|---|---|
| `viewed_on` | `DATE`. 시각이 아니라 날짜 |
| `created_at` | 실제 조회 시각 |

`viewed_on`은 `LocalDate.now()`로 만들어집니다 — **서버 타임존 기준**입니다
(`post/application/PostService.java:51`). 사용자 타임존과 다르면
"하루"의 경계가 어긋납니다. `미결정`

`idx_view_log_date`는 날짜별 집계나 오래된 로그 정리를 위한 것으로 보이나,
현재 그 쿼리를 쓰는 코드가 없습니다.

**이 테이블이 가장 빠르게 자랍니다.** 회원 수 × 글 수 × 활동 일수.
정리 정책이 없습니다. `미결정`

## 엔티티 관계 요약 `확인됨`

```
member 1 ──── N post          (회원 글만. member_id nullable)
member 1 ──── N comment       (필수)
member 1 ──── N verification_token
member 1 ──── N post_like
member 1 ──── N post_view_log

board  1 ──── N post

post   1 ──── N comment
post   1 ──── N attachment    (ON DELETE CASCADE)
post   1 ──── N post_like     (ON DELETE CASCADE)
post   1 ──── N post_view_log (ON DELETE CASCADE)
```

## JPA 매핑 특이사항 `확인됨`

| 항목 | 내용 |
|---|---|
| 연관관계 | 전부 `@ManyToOne(fetch = LAZY)`. `@OneToMany` 컬렉션 없음 |
| 감사 | `BaseTimeEntity` (`Member`, `Post`가 상속). `Comment`는 자체 필드 |
| 시각 타입 | `ZonedDateTime` ↔ `TIMESTAMPTZ` |
| 기본 생성자 | `@NoArgsConstructor(access = PROTECTED)` — 무분별한 생성 방지 |
| 생성 방법 | 정적 팩토리 (`Member.pending`, `Post.member`, `Post.guest`) |

`@OneToMany`를 쓰지 않는 것은 의도적으로 보입니다 —
N+1과 지연 로딩 문제를 원천 차단하고, 필요한 조회는 리포지토리 메서드로 명시합니다.

## 스키마에 없는 것 `확인됨`

| 항목 | 영향 |
|---|---|
| 감사 로그 테이블 | 누가 언제 무엇을 했는지 추적 불가 |
| 약관 동의 기록 | `termsAccepted`가 저장되지 않음 |
| 로그인 이력 | 이상 접근 탐지 불가 |
| 메일 발송 기록 | 발송 성공·실패 집계 불가 |
| 신고·차단 | 커뮤니티 운영 기능 없음 |
| 대댓글 | `comment.parent_id` 없음 |
| 게시글 임시저장 | 없음 |

## 관련 문서

- [erd.md](erd.md) — 관계도
- [migration-policy.md](migration-policy.md) — 스키마 변경 규칙
- [data-retention.md](data-retention.md) — 보존·정리
- [../adr/ADR-002-postgresql.md](../adr/ADR-002-postgresql.md) — DB 기능 의존
- [../../security/data-classification.md](../../security/data-classification.md) — 데이터 등급
