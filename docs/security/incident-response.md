# 침해 사고 대응

> 상태: **골격 문서입니다.** 대응 조직·연락 체계가 정의되어 있지 않습니다.
> 기술적 조치 절차는 코드에서 확인 가능한 범위에서 `확인됨`, 나머지는 `제안`입니다.

## 대응 역량의 현재 한계 `확인됨`

**사고를 탐지하고 조사할 수단이 거의 없습니다.**

| 필요한 것 | 현재 |
|---|---|
| 감사 로그 | **없음** |
| 로그인 시도 기록 | **없음** |
| 권한 거부 기록 | **없음** |
| CAPTCHA 실패 기록 | **없음** |
| 메일 발송 기록 | **없음** |
| 메트릭·알림 | **없음** |
| 로그 보존 | stdout만 — 컨테이너 재시작 시 소멸 |
| 백업 | **없음** |

**사고가 나면 무엇이 일어났는지 재구성할 수 없습니다.**

이 문서의 절차 대부분이 "먼저 로그를 확보한다"로 시작해야 하는데,
확보할 로그가 없다는 것이 가장 큰 문제입니다.

## 대응 조직 `미결정`

| 역할 | 책임 | 담당 |
|---|---|---|
| 최초 대응자 | 탐지, 초기 판단, 격리 | **미지정** |
| 기술 조사 | 원인 분석, 수정 | 미지정 |
| 의사 결정 | 서비스 중단 여부, 공지 | 미지정 |
| 외부 소통 | 사용자 공지, 신고 | 미지정 |

git 이력상 1인 프로젝트로 보이므로 실질적으로 한 사람이 전부 맡게 됩니다.
**연락 가능한 시간과 대체 연락처**를 정해 두는 것이 최소한의 준비입니다. `제안`

## 심각도 기준 `제안`

| 등급 | 기준 | 예 |
|---|---|---|
| **S1** | 데이터 유출 확인, 시스템 장악 | DB 덤프 유출, 관리자 권한 탈취 |
| **S2** | 유출 정황, 계정 다수 침해 | 무차별 대입 성공, 세션 탈취 |
| **S3** | 서비스 영향, 단일 계정 침해 | 디스크 고갈로 중단, 계정 1건 |
| **S4** | 시도만 확인, 실질 피해 없음 | 경로 탈출 시도, 스캔 |

## 공통 초기 대응 `제안`

```
1. 기록 확보      ← 가장 먼저. 컨테이너를 재시작하면 로그가 사라집니다
2. 범위 판단
3. 격리
4. 근본 원인 제거
5. 복구
6. 사후 기록
```

### 1단계가 특히 중요한 이유 `확인됨`

로그가 stdout으로만 나가므로 `docker compose restart` 한 번에 사라집니다.

**조치보다 먼저 로그를 파일로 내리세요.**

```bash
docker compose logs app > incident-$(date +%F-%H%M).log
docker compose logs postgres >> incident-$(date +%F-%H%M).log
```

## IR-001 계정 탈취 의심

### 탐지 신호 `확인됨`

현재 자동 탐지가 없으므로 **사용자 신고가 유일한 경로**입니다.

역설적으로 `maximumSessions(1)` 설정이 도움이 됩니다 —
공격자가 로그인하면 **원 사용자가 즉시 쫓겨나** 이상을 알아차립니다.
(`config/SecurityConfig.java:63`)

### 대응

```
1. 로그 확보
2. 해당 계정의 상태 확인
3. 세션 무효화
4. 비밀번호 재설정 유도
5. 피해 범위 확인 (작성·수정·삭제된 글)
```

### 사용 가능한 수단 `확인됨`

| 조치 | 방법 | 비고 |
|---|---|---|
| 계정 정지 | DB에서 `status = 'SUSPENDED'` | `isAccountNonLocked()`가 즉시 차단 |
| 세션 무효화 | **앱 재시작** | 인메모리 세션이라 전원 로그아웃 |
| 비밀번호 재설정 | 사용자가 직접 요청 | 관리자 강제 리셋 기능 없음 |

```sql
-- 계정 정지 (조건 명확)
UPDATE member SET status = 'SUSPENDED' WHERE username = '<대상>';
```

`SUSPENDED`로 전환하는 코드는 없지만 **판정 로직은 동작합니다.**
DB에서 직접 바꾸면 즉시 로그인이 차단됩니다.

### 한계 `미결정`

| 한계 | 내용 |
|---|---|
| 특정 세션만 무효화 불가 | 전체 재시작 외에 방법 없음 |
| 로그인 이력 없음 | 언제부터 침해됐는지 모름 |
| 감사 로그 없음 | 무엇을 했는지 모름 |
| 관리자 강제 리셋 없음 | 사용자 협조 필요 |

피해 범위는 `post`·`comment`의 `updated_at`으로 추정하는 정도가 한계입니다.

```sql
SELECT id, title, created_at, updated_at, deleted_at
  FROM post WHERE member_id = <id> ORDER BY updated_at DESC;
```

## IR-002 무차별 대입 공격

### 탐지 `확인됨`

**로그인 실패가 기록되지 않아 탐지할 수 없습니다.**

리버스 프록시가 있다면 접근 로그에서 `POST /api/auth/login` 빈도를 볼 수 있지만,
프록시 구성이 문서화되어 있지 않습니다.

### 즉시 대응 `제안`

| 조치 | 방법 |
|---|---|
| 공격 IP 차단 | 프록시·방화벽 수준 |
| 대상 계정 정지 | `status = 'SUSPENDED'` |
| 서비스 일시 중단 | `docker compose stop app` |

애플리케이션 수준에서 할 수 있는 것이 없습니다.

### 사후 조치 `제안`

로그인 실패 이벤트 처리를 추가합니다. **로그만 남겨도 다음 사고에서는 탐지가 가능해집니다.**

```java
@EventListener
public void onFailure(AuthenticationFailureBadCredentialsEvent e) {
    log.warn("로그인 실패: username={} ip={}", ..., ...);
}
```

## IR-003 디스크 고갈 (업로드 남용)

### 탐지 `확인됨`

```bash
docker system df -v | grep board
docker exec <app컨테이너> du -sh /app/uploads
```

디스크가 차면 DB 쓰기와 로그 기록까지 실패해 서비스가 멈춥니다.

### 왜 발생하는가 `확인됨`

`POST /api/files`에 **인증도 속도 제한도 없습니다**(T-002).
누구나 100MB × 무제한으로 업로드할 수 있습니다.

### 즉시 대응 `제안`

```bash
# 1. 고아 파일 확인 — attachment 테이블에 없는 파일
docker exec board-postgres psql -U board_user -d board_db \
  -c "SELECT stored_name FROM attachment;" > referenced.txt
docker exec <app컨테이너> ls /app/uploads > actual.txt
# 두 목록을 비교해 참조되지 않는 파일 식별

# 2. 최근 대량 업로드 확인
docker exec <app컨테이너> ls -lt /app/uploads | head -50
```

**참조되지 않는 파일만** 삭제합니다. `attachment`에 등록된 파일은 지우면 안 됩니다.

### 근본 조치 `제안`

1. `POST /api/files`에 인증 요구
2. 사용자별 업로드 총량·속도 제한
3. 고아 파일 정리 배치 (24시간)

## IR-004 비밀값 유출

### 시나리오

`.env` 커밋, 로그 유출, `MAIL_DEBUG=true` 상태의 로그 노출 등

### 대응 `제안`

| 유출된 값 | 조치 |
|---|---|
| SMTP 비밀번호 | 제공자에서 앱 비밀번호 재발급 → `.env` 갱신 → 재기동 |
| CAPTCHA 시크릿 | provider에서 재발급 |
| DB 비밀번호 | 계정 비밀번호 변경 → 모든 인스턴스 갱신 |
| 세션 | 앱 재시작 (전원 로그아웃) |

**git 이력에서 지워도 push된 값은 유출로 간주하고 반드시 재발급합니다.**

### 특히 주의 — 로그의 인증 토큰 `확인됨`

`LoggingMailSender` 폴백 상태에서는 **메일 본문 전체가 로그에 남습니다.**
본문에는 이메일 인증·비밀번호 재설정 링크(토큰 원문 포함)가 들어 있습니다.

로그를 볼 수 있는 사람이 **누구의 계정이든 인증·재설정할 수 있습니다.**

```bash
# 운영에서 이 로그가 보이면 즉시 조치
docker compose logs app | grep -i "SMTP 설정이 비어 있어"
```

발견 시:
1. SMTP 설정을 채우고 재기동
2. 로그에 노출된 토큰을 무효화

```sql
-- 노출 기간에 발급된 미사용 토큰 무효화
UPDATE verification_token
   SET used_at = now()
 WHERE used_at IS NULL
   AND expires_at > now();
```

조건이 명확한 UPDATE입니다. 해당 사용자들은 재발송을 받아야 합니다.

## IR-005 데이터 손실

### 현실 `확인됨`

**백업이 없습니다.**

| 대상 | 백업 |
|---|---|
| `board_data` (DB) | 없음 |
| `board_uploads` (첨부) | 없음 |

볼륨이 손실되면 **복구할 방법이 없습니다.**

### 완화 요소 `확인됨`

| 요소 | 효과 |
|---|---|
| `clean-disabled: true` | Flyway가 스키마를 지우지 않음 |
| `clean-on-validation-error: false` | 검증 실패가 삭제로 이어지지 않음 |
| `ddl-auto: validate` | Hibernate가 스키마를 바꾸지 않음 |
| 소프트 삭제 | 사용자 삭제는 행을 지우지 않음 |

**애플리케이션이 데이터를 지우는 경로는 사실상 없습니다.**
위험은 인프라 조작 실수(`docker volume rm`)와 하드웨어 장애입니다.

### 최우선 조치 `제안`

사고 대응 이전에 **백업부터** 만드세요.

```bash
docker exec board-postgres pg_dump -U board_user board_db > backup-$(date +%F).sql
docker run --rm -v <프로젝트명>_board_uploads:/data -v "$PWD":/backup \
  alpine tar czf /backup/uploads-$(date +%F).tar.gz -C /data .
```

## 절대 하지 말 것 `확인됨`

사고 대응 중 당황해서 하기 쉬운 실수들입니다.
프로젝트 `CLAUDE.md`의 금지 목록과 일치합니다.

| 금지 | 이유 |
|---|---|
| **조치 전 컨테이너 재시작** | 로그가 사라져 조사가 불가능해짐 |
| `flyway clean` | 스키마 전체 삭제 |
| `clean-on-validation-error: true`로 변경 | 검증 실패 = DB 삭제 |
| `DROP TABLE` / `TRUNCATE` | 데이터 손실 |
| 조건 없는 `DELETE` | 데이터 손실 |
| `docker volume rm` | **백업이 없음** |
| 대상 DB 확인 없이 SQL 실행 | `PGSQL_HOST`가 PC마다 다름 |

마지막이 특히 중요합니다.

```bash
# SQL 실행 전 반드시
grep PGSQL_HOST .env
docker compose logs app | grep -i "jdbc:postgresql"
```

## 사후 기록 `제안`

이 프로젝트에는 이미 좋은 관행이 있습니다 — `todo.md`의 버그 기록 형식입니다.

```
- 증상
- 원인 (파일·라인까지)
- 조치
- 함께 고친 것
- 회귀 테스트
- 관련 문서
```

보안 사고도 같은 형식으로 남기되, **민감 정보는 제외**합니다.
그리고 [threat-model.md](threat-model.md)에 새 위협으로 반영합니다.

## 대응 역량을 갖추기 위한 선행 조치 `제안`

이 문서가 실제로 쓰이려면 아래가 먼저 필요합니다.

| 순위 | 조치 | 이유 |
|---|---|---|
| 1 | **백업** | 없으면 복구가 불가능 |
| 2 | 로그 영속화 | 재시작하면 증거가 사라짐 |
| 3 | 로그인 실패·CAPTCHA 실패 로그 | 탐지의 최소 조건 |
| 4 | 감사 로그 (누가 무엇을) | 피해 범위 파악 |
| 5 | 헬스체크·알림 | 인지 시점 단축 |
| 6 | 대응 담당자·연락처 확정 | 조직적 준비 |

**1~3이 없으면 이 문서의 대부분이 실행 불가능합니다.**

## 관련 문서

- [threat-model.md](threat-model.md) — 예상 위협
- [vulnerability-management.md](vulnerability-management.md) — 취약점 관리
- [secrets-management.md](secrets-management.md) — 비밀값 유출 대응
- [../technology/infrastructure/runbooks/README.md](../technology/infrastructure/runbooks/README.md) — 장애 런북
- [../technology/infrastructure/observability.md](../technology/infrastructure/observability.md) — 관측 수단
