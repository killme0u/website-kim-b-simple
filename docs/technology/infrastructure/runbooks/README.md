# 런북

> 상태: 절차는 `확인됨`(코드·설정에 근거), 대상 장애는 실제로 보고된 것입니다.

장애 상황별 진단·대응 절차입니다. 각 런북은 **증상에서 시작해 원인을 좁히는** 순서로 씁니다.

## 목록

| ID | 제목 | 상태 |
|---|---|---|
| [RB-001](RB-001-mail-not-sent.md) | 메일이 발송되지 않음 | **현재 열린 문제** (`todo.md`) |
| [RB-002](RB-002-flyway-validation-failure.md) | Flyway 검증 실패로 기동 불가 | 예방적 |

## 런북이 필요하지만 아직 없는 것 `제안`

| 증상 | 우선순위 | 비고 |
|---|---|---|
| 디스크 가득 참 (업로드) | 높음 | 업로드에 인증이 없어 발생 가능 |
| DB 연결 실패 | 높음 | |
| 로그인이 되지 않음 (CSRF 403) | 중간 | `SecurityConfigTest`가 회귀를 막고 있음 |
| CAPTCHA 실패로 가입 불가 | 중간 | provider 확정 후 |
| 응답 지연 | 낮음 | 측정 수단이 없어 진단 불가 |

## 공통 진단 명령 `확인됨`

관측 인프라가 없으므로(Actuator 미도입) 로그와 DB 직접 조회가 유일한 수단입니다.

### 컨테이너 상태

```bash
docker compose ps
docker compose logs app --tail 200
docker compose logs postgres --tail 50
```

### 기동 시 어떤 선택을 했는지

`MailConfig`가 기동 시 발송기 선택을 로그로 남깁니다.

```bash
docker compose logs app | grep -iE "SMTP (메일 발송을 사용|설정이 비어)"
```

| 로그 | 의미 |
|---|---|
| `SMTP 메일 발송을 사용합니다. host=... from=...` | 실제 발송기 |
| `SMTP 설정이 비어 있어(host='', username 설정됨=false) ...` | **로그 전용 폴백** |

### DB 상태

```bash
# 마이그레이션 이력
docker exec board-postgres psql -U board_user -d board_db \
  -c "SELECT installed_rank, version, description, success, installed_on
        FROM flyway_schema_history ORDER BY installed_rank;"

# 계정 상태 분포
docker exec board-postgres psql -U board_user -d board_db \
  -c "SELECT status, count(*) FROM member GROUP BY status;"

# 게시판 정책 현재 값
docker exec board-postgres psql -U board_user -d board_db \
  -c "SELECT slug, requires_auth_to_read, requires_auth_to_write,
             allows_comment, allows_attachment FROM board ORDER BY display_order;"
```

### 디스크

```bash
docker system df -v | grep board
docker exec <app컨테이너> du -sh /app/uploads
```

## 안전 규칙 `확인됨`

진단·복구 중에 **절대 하지 말 것**입니다.
프로젝트 `CLAUDE.md`의 금지 작업 목록과 일치합니다.

| 금지 | 이유 |
|---|---|
| `flyway clean` | 스키마 전체 삭제. `clean-disabled: true`로 막혀 있으나 설정을 바꾸지 말 것 |
| `clean-on-validation-error: true`로 변경 | 검증 실패가 DB 삭제로 이어짐 |
| `ddl-auto`를 `update`·`create`로 변경 | Flyway와 충돌, 예측 불가능한 스키마 |
| `DROP TABLE` / `DROP DATABASE` | 데이터 손실 |
| 조건 없는 `DELETE` / `TRUNCATE` | 데이터 손실 |
| `docker volume rm board_data` | **백업이 없습니다** |
| 적용된 마이그레이션 파일 수정 | 체크섬 불일치로 기동 불가 |

**특히 위험한 이유**: `PGSQL_HOST`가 PC마다 다르게 설정됩니다.
지금 접속한 DB가 내 로컬인지 다른 사람의 것인지 확인하지 않고 파괴적 명령을 실행하면
남의 데이터를 지울 수 있습니다.

```bash
# 지금 어느 DB 를 보고 있는지 먼저 확인
docker compose logs app | grep -i "jdbc:postgresql"
grep PGSQL_HOST .env
```

## 런북 작성 규칙 `제안`

새 런북은 아래 형식을 따릅니다.

```
# RB-00N 증상 한 줄

## 증상          사용자·모니터링이 관측하는 것
## 영향 범위      누가 무엇을 못 하는가
## 빠른 확인      1~2분 안에 원인을 좁히는 명령
## 원인별 대응     확인 결과에 따른 분기
## 예방          재발 방지
## 관련 문서
```

**추측이 아니라 코드 근거를 인용**합니다. 어떤 조건에서 어떤 분기가 도는지
파일·라인으로 적으면 다음 사람이 검증할 수 있습니다.

## 관련 문서

- [../observability.md](../observability.md) — 관측 수단 현황
- [../deployment-guide.md](../deployment-guide.md) — 배포 절차
- [../../../security/incident-response.md](../../../security/incident-response.md) — 보안 사고 대응
