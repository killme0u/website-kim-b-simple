# RB-002 Flyway 검증 실패로 기동 불가

> 상태: 절차는 `확인됨` — `application.yml` 설정과 Flyway 동작에 근거합니다.
> 예방적 런북입니다(실제 발생 보고는 없음).

## 증상

애플리케이션이 기동 중 실패합니다.

```
FlywayValidateException: Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 3
```

또는

```
SchemaManagementException: Schema-validation: missing column [nickname] in table [member]
```

앞은 Flyway, 뒤는 Hibernate `ddl-auto: validate`입니다.

## 좋은 소식 — DB는 안전합니다 `확인됨`

```yaml
flyway:
  clean-disabled: true
  clean-on-validation-error: false
```

이 설정 덕분에 **검증 실패가 스키마 삭제로 이어지지 않습니다.**
기동만 실패하고 데이터는 그대로 있습니다.

이전 설정(`clean-disabled: false` + `clean-on-validation-error: true`)에서는
같은 상황에서 **DB가 통째로 지워졌습니다.**
`application.yml` 주석에 그 경위가 기록되어 있습니다.

**이 두 줄을 절대 되돌리지 마세요.** `PGSQL_HOST`가 PC마다 다른 이 프로젝트에서는
남의 DB를 지울 수 있습니다.

## 빠른 확인 (1분)

### 1. 지금 어느 DB를 보고 있는가 `확인됨`

**가장 먼저 확인할 것입니다.**

```bash
grep PGSQL_HOST .env
docker compose logs app | grep -i "jdbc:postgresql"
```

의도한 DB가 맞는지 확인합니다.
"내 로컬인 줄 알았는데 학원 PC의 DB였다" 같은 상황이 이 프로젝트에서 실제로 가능합니다.

### 2. 마이그레이션 이력

```bash
docker exec board-postgres psql -U board_user -d board_db -c "
SELECT installed_rank, version, description, checksum, success, installed_on
  FROM flyway_schema_history ORDER BY installed_rank;"
```

| 관찰 | 원인 |
|---|---|
| `success = false`인 행 | → 원인 C |
| 파일보다 이력이 많음 | → 원인 B |
| 파일보다 이력이 적음 | 정상 (다음 기동에 적용됨) |
| 개수는 같은데 체크섬 불일치 | → 원인 A |

## 원인 A — 적용된 마이그레이션 파일을 수정함 `확인됨`

가장 흔한 원인입니다.

Flyway는 각 마이그레이션의 체크섬을 `flyway_schema_history`에 저장합니다.
파일이 한 글자라도 바뀌면 체크섬이 달라져 검증에 실패합니다.

### 확인

```bash
git log --oneline -- backend-springboot/src/main/resources/db/migration/
git diff HEAD~1 -- backend-springboot/src/main/resources/db/migration/
```

### 대응 — 개발 DB (버려도 되는 경우)

**`flyway clean`을 쓰지 마세요.** 볼륨을 지우는 편이 대상이 명확합니다.

```bash
# 지우려는 볼륨이 맞는지 먼저 확인
docker volume ls | grep board

docker compose down
docker volume rm <프로젝트명>_board_data
docker compose up -d
```

### 대응 — 보존해야 하는 DB

**파일을 원래대로 되돌리고, 변경은 새 마이그레이션으로 만듭니다.**

```bash
# 1. 수정된 파일을 원복
git checkout HEAD~1 -- backend-springboot/src/main/resources/db/migration/V3__add_member_nickname.sql

# 2. 원하는 변경을 새 파일로
#    V5__alter_member_nickname.sql
```

이것이 [../../data/migration-policy.md](../../data/migration-policy.md)가
"적용된 마이그레이션을 수정하지 말라"고 하는 이유입니다.

### 최후 수단 — 체크섬 복구 `제안`

파일 내용이 실제 스키마와 **일치한다고 확신할 때만** 씁니다
(예: 줄바꿈 문자만 CRLF↔LF로 바뀐 경우).

Flyway의 `repair`는 체크섬만 갱신하고 데이터를 건드리지 않습니다.
다만 이 프로젝트에는 Flyway CLI나 Gradle 플러그인이 설정되어 있지 않아
직접 실행할 수단이 없습니다. 필요하면 `flyway_schema_history`의
`checksum` 값을 수동으로 갱신해야 하며, 그 전에 **반드시 백업**하세요.

## 원인 B — 다른 브랜치의 마이그레이션이 적용됨 `확인됨`

DB에는 V5가 적용됐는데 현재 브랜치에는 V4까지만 있는 경우입니다.

Flyway는 이력에 있는데 파일이 없는 마이그레이션을 발견하면 검증에 실패합니다.

### 확인

```bash
# 이력
docker exec board-postgres psql -U board_user -d board_db \
  -c "SELECT version, description FROM flyway_schema_history ORDER BY installed_rank;"

# 파일
ls backend-springboot/src/main/resources/db/migration/
```

### 대응

| 상황 | 대응 |
|---|---|
| 그 마이그레이션이 있는 브랜치로 전환하면 됨 | `git switch <브랜치>` |
| 개발 DB라 버려도 됨 | 볼륨 삭제 후 재생성 |
| 보존해야 함 | 해당 마이그레이션 파일을 현재 브랜치로 가져옴 |

여러 브랜치가 같은 DB를 공유하면 반복해서 발생합니다.
브랜치별로 다른 DB를 쓰는 것이 근본 해결입니다. `제안`

## 원인 C — 마이그레이션이 중간에 실패함 `확인됨`

`success = false`인 행이 있으면 이 경우입니다.

PostgreSQL은 DDL도 트랜잭션 안에서 실행되므로 대부분 자동 롤백되지만,
Flyway 이력에는 실패 기록이 남아 다음 기동을 막습니다.

### 확인

```bash
docker exec board-postgres psql -U board_user -d board_db -c "
SELECT installed_rank, version, description, success
  FROM flyway_schema_history WHERE success = false;"

# 실패 원인은 앱 로그에
docker compose logs app | grep -A 30 -i "migration"
```

### 대응

```sql
-- 1. 실패한 마이그레이션이 스키마에 부분적으로 반영됐는지 확인
--    (PostgreSQL 은 보통 롤백되지만 확인 필요)

-- 2. 실패 기록 삭제 — 대상이 명확한 조건부 삭제
DELETE FROM flyway_schema_history WHERE success = false;

-- 3. 마이그레이션 파일을 고친 뒤 재기동
```

`DELETE`에 `WHERE success = false` 조건이 있으므로 안전합니다.
**조건 없는 `DELETE`나 `TRUNCATE`는 절대 쓰지 마세요.**

## 원인 D — Hibernate validate 실패 `확인됨`

Flyway는 통과했는데 Hibernate가 실패하는 경우입니다.

```
Schema-validation: missing column [xxx] in table [yyy]
```

**엔티티에 필드를 추가하고 마이그레이션을 안 쓴 것**이 원인입니다.

`ddl-auto: validate`라 Hibernate는 스키마를 만들지 않습니다.
스키마 변경은 반드시 마이그레이션으로 해야 합니다.

### 대응

```sql
-- V5__add_xxx.sql
ALTER TABLE yyy ADD COLUMN xxx VARCHAR(50);
```

**`ddl-auto`를 `update`로 바꾸지 마세요.** Flyway와 충돌하고,
스키마가 환경마다 달라져 추적할 수 없게 됩니다.

## 원인 E — DB에 접속하지 못함 `확인됨`

Flyway 오류처럼 보이지만 실은 접속 문제인 경우입니다.

```
Unable to obtain connection from database
```

### 확인

```bash
# 설정된 주소
grep -E "^PGSQL_(HOST|PORT)" .env
docker compose exec app printenv SPRING_DATASOURCE_URL

# 컨테이너 상태
docker compose ps postgres
docker compose logs postgres --tail 30
```

### 흔한 원인 `확인됨`

| 원인 | 확인 |
|---|---|
| `PGSQL_HOST=` (줄은 두고 값만 비움) | 빈 호스트. **줄을 통째로 지워야** 기본값이 쓰임 |
| 다른 PC의 Docker인데 IP가 바뀜 | `.env`의 IP 갱신 |
| postgres가 아직 준비되지 않음 | `depends_on`에 `condition`이 없어 발생 가능 |
| 5432가 방화벽에 막힘 | 네트워크 확인 |

### `depends_on` 경합 `확인됨`

```yaml
depends_on:
  - postgres
```

**시작 순서만 보장하고 준비 상태는 보지 않습니다.**
postgres가 초기화 중인데 app이 먼저 접속을 시도하면 실패합니다.

즉시 대응:

```bash
docker compose restart app
```

근본 해결: 헬스체크 + `condition: service_healthy`
→ [../observability.md](../observability.md)

## 예방 `제안`

| 조치 | 효과 |
|---|---|
| 적용된 마이그레이션을 절대 수정하지 않기 | 원인 A 제거 |
| 브랜치별 DB 분리 | 원인 B 제거 |
| CI에서 빈 DB 마이그레이션 테스트 | 원인 C 조기 발견 |
| postgres 헬스체크 + `service_healthy` | 원인 E 제거 |
| 배포 전 DB 백업 | 최악의 경우 복구 |

## 절대 하지 말 것 `확인됨`

| 금지 | 결과 |
|---|---|
| `clean-disabled: false`로 변경 | 스키마 삭제 가능해짐 |
| `clean-on-validation-error: true`로 변경 | **검증 실패 = DB 삭제** |
| `flyway clean` 실행 | 스키마 전체 삭제 |
| `ddl-auto`를 `create`·`update`로 | Flyway와 충돌 |
| 확인 없이 `docker volume rm` | 백업이 없음 |

프로젝트 `CLAUDE.md`의 금지 작업 목록과 일치합니다.

## 관련 문서

- [README.md](README.md) — 공통 진단·안전 규칙
- [../../data/migration-policy.md](../../data/migration-policy.md) — 마이그레이션 규칙
- [../../data/data-model.md](../../data/data-model.md) — 현재 스키마
- [../../adr/ADR-002-postgresql.md](../../adr/ADR-002-postgresql.md)
