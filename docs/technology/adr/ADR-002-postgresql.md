# ADR-002 PostgreSQL 채택과 DB 기능 의존

> 상태: **채택됨** `확인됨` — 스키마와 쿼리에 반영되어 있습니다.

## 맥락

게시판에는 "중복을 막아야 하는" 규칙이 여럿 있습니다.

| 규칙 | 성격 |
|---|---|
| 같은 회원이 같은 날 같은 글을 봐도 조회수는 1회만 | 동시성 |
| 한 회원은 한 글에 좋아요 1회만 | 동시성 |
| 아이디·이메일은 유일 | 유일성 |
| 닉네임은 유일하되 **선택 항목** (없어도 됨) | 부분 유일성 |
| 회원 글과 비회원 글은 배타적 | 무결성 |

이 규칙들을 애플리케이션 코드에서 "조회 후 판단"으로 구현하면
동시 요청에서 경합이 발생합니다. 두 요청이 동시에 "없음"을 확인하고
둘 다 INSERT하는 상황을 막을 수 없습니다.

## 검토한 선택지

### A. 애플리케이션 레벨 검사 + 일반 SQL

`SELECT`로 확인하고 없으면 `INSERT`.

| 장점 | 단점 |
|---|---|
| DB 이식성 | 경합 조건 존재 |
| 로직이 코드에 보임 | 락을 걸면 성능 저하 |
| | 규칙이 코드 여러 곳에 흩어짐 |

### B. PostgreSQL 제약 + 전용 문법 (채택)

복합 기본키와 `ON CONFLICT DO NOTHING`, 부분 UNIQUE 인덱스를 씁니다.

| 장점 | 단점 |
|---|---|
| 경합 조건이 구조적으로 불가능 | PostgreSQL 종속 |
| 규칙이 스키마 한 곳에 | 네이티브 쿼리 필요 |
| 검사 코드가 필요 없음 | JPA 추상화를 벗어남 |

### C. 분산 락 (Redis 등)

의존성이 하나 늘고, 락 해제 실패·타임아웃을 다뤄야 합니다.
DB 제약으로 충분한 문제에 과도합니다.

## 결정

**B를 채택합니다.** PostgreSQL 15를 쓰고, 중복 방지 규칙은 DB 제약으로 표현합니다.

## 사용하는 PostgreSQL 기능 `확인됨`

### 1. 복합 기본키 = 규칙

```sql
CREATE TABLE post_view_log (
    post_id    BIGINT NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    member_id  BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    viewed_on  DATE   NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, member_id, viewed_on)
);

CREATE TABLE post_like (
    post_id   BIGINT NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    member_id BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, member_id)
);
```

PK가 곧 규칙입니다. "하루에 한 번", "한 사람당 한 번"을 따로 검사하지 않습니다.

### 2. `INSERT ... ON CONFLICT DO NOTHING` `확인됨`

```java
@Modifying
@Query(value = """
    INSERT INTO post_view_log (post_id, member_id, viewed_on)
    VALUES (:postId, :memberId, :viewedOn)
    ON CONFLICT DO NOTHING
    """, nativeQuery = true)
int tryRecord(Long postId, Long memberId, LocalDate viewedOn);
```

(`post/domain/PostViewLogRepository.java:11-19`)

**반환값이 판정 결과입니다.**

| 반환 | 뜻 | 동작 |
|---|---|---|
| 1 | 처음 봄 | `increaseViewCount` 호출 |
| 0 | 오늘 이미 봄 | 아무것도 안 함 |

조회-판단-삽입이 한 문장이라 경합이 없습니다.
`nativeQuery = true`가 필요합니다 — JPQL에는 `ON CONFLICT`가 없습니다.

### 3. 부분 UNIQUE 인덱스 `확인됨`

```sql
-- V3__add_member_nickname.sql
ALTER TABLE member ADD COLUMN nickname VARCHAR(30);

CREATE UNIQUE INDEX ux_member_nickname
    ON member (nickname)
    WHERE nickname IS NOT NULL;
```

닉네임은 **선택 항목**입니다. 여러 회원이 닉네임 없이(`NULL`) 있을 수 있어야 하지만,
값이 있으면 유일해야 합니다.

일반 UNIQUE 제약은 대부분의 DB에서 `NULL`을 중복으로 보지 않지만,
`WHERE` 절을 붙인 부분 인덱스는 의도를 명시적으로 표현합니다.

이 설계가 `Member.normalizeNickname`이 빈 문자열을 `null`로 바꾸는 이유입니다.
`""`가 여럿 들어오면 UNIQUE 위반이 되지만 `NULL`은 괜찮습니다.

### 4. CHECK 제약으로 배타 관계 `확인됨`

```sql
CONSTRAINT post_author_ck CHECK (
    (member_id IS NOT NULL AND guest_nickname IS NULL AND guest_password_hash IS NULL)
    OR
    (member_id IS NULL AND guest_nickname IS NOT NULL AND guest_password_hash IS NOT NULL)
)
```

회원 글과 비회원 글이 섞인 상태를 DB가 거부합니다.
`Post.member()`와 `Post.guest()` 정적 팩토리가 이 제약과 짝을 이룹니다.

### 5. `TIMESTAMPTZ` `확인됨`

모든 시각 컬럼이 `TIMESTAMPTZ`(타임존 포함)입니다.
Java 쪽은 `ZonedDateTime`으로 받습니다.
서버·DB·클라이언트의 타임존이 달라도 시점이 어긋나지 않습니다.

### 6. `BIGSERIAL` `확인됨`

`GenerationType.IDENTITY`와 짝을 이룹니다.

## 결과

### 얻은 것

| 항목 | 효과 |
|---|---|
| 경합 조건 제거 | 조회수·좋아요 중복이 구조적으로 불가능 |
| 코드 단순화 | 중복 검사 로직이 없음 |
| 규칙의 단일 위치 | 스키마를 보면 규칙이 보임 |
| 최종 방어선 | 애플리케이션 버그가 데이터를 망치지 못함 |

`PRD.md` 2.6이 이를 "복합 기본키가 곧 규칙"이라고 표현합니다.

### 치른 대가 `확인됨`

| 항목 | 영향 |
|---|---|
| DB 종속 | MySQL·H2로 옮기려면 `ON CONFLICT`와 부분 인덱스를 다시 써야 함 |
| 네이티브 쿼리 | JPA 추상화를 벗어난 곳이 생김 |
| 테스트 DB | 인메모리 H2로 대체 불가 — 실제 PostgreSQL 필요 |
| 규칙이 코드에 안 보임 | 스키마를 봐야 이해됨 |

### 테스트에 미치는 영향 `미결정`

`ON CONFLICT`와 부분 UNIQUE 인덱스 때문에 H2로는 조회수 로직을 테스트할 수 없습니다.
현재 테스트 5개 중 DB를 쓰는 것이 없어 문제가 드러나지 않지만,
조회수 중복 방지(AC-006)를 검증하려면 Testcontainers 같은 수단이 필요합니다.
→ [../testing-strategy.md](../testing-strategy.md)

## 안전장치 `확인됨`

DB에 규칙을 두는 만큼 스키마를 지키는 설정이 중요합니다.

```yaml
jpa:
  hibernate:
    ddl-auto: validate      # Hibernate가 스키마를 만들지 않음
flyway:
  clean-disabled: true                # 스키마 전체 삭제 차단
  clean-on-validation-error: false    # 검증 실패해도 삭제 안 함
```

이전 설정(`clean-disabled: false` + `clean-on-validation-error: true`)에서는
**마이그레이션 검증이 실패하면 앱이 뜨는 것만으로 대상 DB가 통째로 지워졌습니다.**
PC마다 다른 DB를 보는 구성에서 특히 위험해 바뀌었습니다.

검증 실패는 삭제가 아니라 **기동 실패**로 드러나야 합니다.

## 버전 고정 `확인됨`

| 위치 | 버전 |
|---|---|
| `docker-compose.yml` | `postgres:15-alpine` |
| 드라이버 | `org.postgresql:postgresql` (Boot BOM 관리) |
| Flyway | `flyway-database-postgresql` |

위 기능들은 PostgreSQL 9.5(`ON CONFLICT`)·9.0(부분 인덱스) 이상이면 동작하므로
15는 여유 있는 선택입니다.

## 재검토 조건 `제안`

- 다른 DB 엔진을 써야 하는 외부 제약이 생김
- 조회수 규모가 커져 `post_view_log`가 병목이 됨 (파티셔닝·집계 테이블 검토)
- 인메모리 DB로 빠른 테스트가 필요해짐 (Testcontainers가 더 나은 답일 가능성)

## 관련 문서

- [ADR-001-modular-monolith.md](ADR-001-modular-monolith.md)
- [../data/data-model.md](../data/data-model.md) — 스키마 전체
- [../data/migration-policy.md](../data/migration-policy.md) — 마이그레이션 규칙
- [../../features/state-machines.md](../../features/state-machines.md) — SM-006 조회 기록
