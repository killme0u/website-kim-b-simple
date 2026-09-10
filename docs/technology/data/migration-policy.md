# 마이그레이션 정책

> 상태: `확인됨` — `application.yml` 설정과 실제 마이그레이션 파일에서 도출.

## 원칙 — 스키마의 유일한 출처는 Flyway `확인됨`

```yaml
jpa:
  hibernate:
    ddl-auto: validate
```

Hibernate는 스키마를 **만들지도 바꾸지도 않습니다.** 기동 시 엔티티와 실제 스키마가
일치하는지 확인만 합니다. 어긋나면 기동이 실패합니다.

따라서 컬럼을 추가하려면 **반드시 마이그레이션 파일을 써야 합니다.**
엔티티에 필드만 추가하면 앱이 뜨지 않습니다.

## 안전장치 `확인됨`

```yaml
flyway:
  enabled: true
  baseline-on-migrate: true
  clean-disabled: true
  clean-on-validation-error: false
```

| 설정 | 효과 |
|---|---|
| `clean-disabled: true` | **스키마 전체 삭제를 차단** |
| `clean-on-validation-error: false` | 검증 실패해도 삭제하지 않음 |
| `baseline-on-migrate: true` | 이력 테이블이 없는 기존 DB에서도 시작 가능 |

### 왜 이 설정인가 `확인됨`

`application.yml`에 이유가 주석으로 적혀 있습니다.

이전 설정은 `clean-disabled: false` + `clean-on-validation-error: true`였습니다.
그 조합에서는 **마이그레이션 검증이 실패하면 앱이 뜨는 것만으로 대상 DB가 통째로 지워집니다.**

PC마다 다른 DB를 보는 이 프로젝트 구성에서는 특히 위험합니다
(`PGSQL_HOST`를 잘못 두면 남의 DB를 지울 수 있습니다).

**검증 실패는 삭제가 아니라 기동 실패로 드러나야 합니다.**

이 원칙은 프로젝트 `CLAUDE.md`의 "Forbidden Destructive Database Examples"와도 일치합니다 —
Flyway clean은 명시적으로 금지된 작업입니다.

## 현재 마이그레이션 `확인됨`

위치: `backend-springboot/src/main/resources/db/migration/`

| 버전 | 파일 | 내용 |
|---|---|---|
| V1 | `V1__init.sql` | 8개 테이블, 인덱스, CHECK 제약 |
| V2 | `V2__seed_board.sql` | 게시판 3개 시드 |
| V3 | `V3__add_member_nickname.sql` | `member.nickname` + 부분 UNIQUE 인덱스 |
| V4 | `V4__member_only_board_read.sql` | `qna`·`archive`를 읽기 회원제로 |

### V1의 특이사항 `확인됨`

파일 첫 바이트에 **BOM이 있습니다** (`﻿-- V1__init.sql`).
현재 동작에는 문제가 없으나, 새 파일은 BOM 없는 UTF-8로 쓰는 것이 안전합니다.

### V2와 V4를 함께 봐야 하는 이유 `확인됨`

```sql
-- V2: 전부 requires_auth_to_read = false 로 시드
VALUES ('free', '자유게시판', false, false, false, false, 1),
       ('qna',  'Q&A 게시판',  false, true,  true,  false, 2),
       ('archive', '자료실',    false, true,  false, true,  3);

-- V4: qna, archive 를 true 로 변경
UPDATE board SET requires_auth_to_read = TRUE WHERE slug IN ('qna', 'archive');
```

**V2만 읽고 현재 정책을 판단하면 틀립니다.**

V2를 직접 수정하지 않고 V4를 추가한 것이 올바른 선택입니다 —
이미 적용된 마이그레이션을 고치면 체크섬이 어긋나 검증이 실패합니다.

### V4의 되돌리기 용이성 `확인됨`

`todo.md`에 기록되어 있습니다.

> 되돌리려면 `requires_auth_to_read`를 `false`로 바꾸는 마이그레이션 한 줄이면 됨.

정책을 **데이터로** 표현한 덕분입니다. 코드 분기였다면 배포가 필요했을 것입니다.

## 작성 규칙 `제안`

### 명명

```
V{번호}__{설명}.sql
```

| 규칙 | 예 |
|---|---|
| 버전은 정수 순차 | `V5`, `V6` |
| 구분자는 **언더스코어 2개** | `V5__add_audit_log.sql` |
| 설명은 소문자 + 언더스코어 | `add_member_nickname` |
| 동사로 시작 | `add_`, `create_`, `alter_`, `drop_`, `seed_` |

### 절대 하지 말 것 `확인됨`

| 금지 | 이유 |
|---|---|
| **적용된 마이그레이션 수정** | 체크섬 불일치 → 기동 실패 |
| **적용된 마이그레이션 삭제** | 동일 |
| `flyway clean` | 스키마 전체 삭제. `clean-disabled: true`로 차단되어 있음 |
| `DROP TABLE` / `DROP SCHEMA` | 데이터 손실 |
| 조건 없는 `DELETE` / `TRUNCATE` | 데이터 손실 |
| `ddl-auto`를 `update`·`create`로 변경 | Flyway와 충돌, 예측 불가능한 스키마 |

이미 커밋·적용된 마이그레이션이 잘못됐다면 **새 마이그레이션으로 고칩니다.**

### 되돌리기 `제안`

Flyway Community에는 `undo`가 없습니다.
되돌리려면 **역방향 마이그레이션을 새로 씁니다.**

```sql
-- V6__revert_member_only_board_read.sql
UPDATE board SET requires_auth_to_read = FALSE WHERE slug IN ('qna', 'archive');
```

따라서 **되돌리기 쉬운 변경을 선호**해야 합니다.

| 변경 | 되돌리기 |
|---|---|
| 컬럼 추가 (nullable) | 쉬움 |
| 데이터 UPDATE | 쉬움 (V4 사례) |
| 컬럼 삭제 | **불가** — 데이터가 사라짐 |
| 컬럼 타입 변경 | 어려움 |
| NOT NULL 추가 | 기존 데이터에 따라 실패 |

## 안전한 변경 패턴 `제안`

### 컬럼 추가

```sql
-- 1단계: nullable 로 추가
ALTER TABLE member ADD COLUMN new_field VARCHAR(50);
```

기존 행에 영향이 없습니다. V3가 이 패턴을 씁니다.

NOT NULL이 필요하면 세 단계로 나눕니다.

```sql
-- V(n)   : nullable 로 추가
-- V(n+1) : 기존 행 채우기 (UPDATE ... WHERE new_field IS NULL)
-- V(n+2) : ALTER COLUMN SET NOT NULL
```

한 번에 하면 기존 행이 있을 때 실패합니다.

### 컬럼 제거

즉시 지우지 말고 두 단계로 나눕니다.

```
1. 애플리케이션에서 사용 중단 (배포)
2. 다음 릴리스에서 DROP COLUMN
```

배포 중 구버전과 신버전이 공존하는 순간을 견디기 위해서입니다.

**현재 제거 후보** `미결정`:
`must_change_password`, `temp_password_expires_at` — 정책을 구현하거나 제거해야 합니다.
`phone` — 필수 수집인데 사용처가 없습니다.

### 인덱스 추가

큰 테이블에는 `CONCURRENTLY`를 고려합니다.

```sql
CREATE INDEX CONCURRENTLY idx_post_member ON post (member_id, deleted_at, id DESC);
```

주의: `CONCURRENTLY`는 **트랜잭션 안에서 실행할 수 없습니다.**
Flyway 스크립트에 `-- flyway:executeInTransaction=false`를 붙여야 합니다.

## 개발 중 스키마 실험 `제안`

마이그레이션을 확정하기 전에 이것저것 시도하고 싶을 때:

1. **버려도 되는 DB인지 확인** — `PGSQL_HOST`가 개인 로컬을 가리키는지
2. 컨테이너를 지우고 다시 만듭니다

```bash
docker compose down
docker volume rm website-kim-b-simple_board_data
docker compose up -d postgres
```

**`flyway clean`을 켜지 마세요.** 설정을 임시로 바꾸면 그대로 커밋될 위험이 있고,
그 설정이 다른 PC에서 남의 DB를 지울 수 있습니다.

볼륨을 지우는 방식은 대상이 명확합니다 — 어떤 볼륨인지 이름으로 보입니다.

## 검증 `제안`

마이그레이션을 추가한 뒤 확인할 것:

- [ ] 앱이 기동하는가 (`validate`가 통과하는가)
- [ ] 엔티티 필드와 컬럼이 일치하는가
- [ ] 기존 데이터가 있는 DB에서도 성공하는가
- [ ] [data-model.md](data-model.md)를 갱신했는가
- [ ] [erd.md](erd.md)를 갱신했는가
- [ ] 되돌릴 방법이 있는가

마지막 항목이 가장 자주 빠집니다.

## 없는 것 `미결정`

| 항목 | 영향 |
|---|---|
| 마이그레이션 자동 테스트 | 빈 DB에서만 검증. 기존 데이터가 있을 때 실패할 수 있음 |
| 롤백 스크립트 | 역방향을 매번 수동 작성 |
| 운영 적용 전 검토 절차 | 기동과 동시에 자동 실행 |
| 배포 전 백업 | `board_data` 볼륨 백업 없음 |

**마이그레이션이 앱 기동과 동시에 자동 실행됩니다.** 검토 없이 운영 DB에 적용됩니다.
운영 규모가 커지면 마이그레이션을 배포와 분리하는 것을 검토해야 합니다. `제안`

## 관련 문서

- [data-model.md](data-model.md) — 현재 스키마
- [erd.md](erd.md) — 관계도
- [data-retention.md](data-retention.md) — 보존 정책
- [../infrastructure/runbooks/RB-002-flyway-validation-failure.md](../infrastructure/runbooks/RB-002-flyway-validation-failure.md) — 검증 실패 대응
