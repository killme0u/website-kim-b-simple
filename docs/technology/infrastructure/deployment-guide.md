# 배포 가이드

> 상태: 로컬·컨테이너 절차는 `확인됨`(README·compose·Dockerfile 기준),
> 운영 배포는 `제안`입니다 — **운영 환경이 정의되어 있지 않습니다.**

## 사전 준비 `확인됨`

| 항목 | 필요 버전 |
|---|---|
| JDK | 25 (Gradle toolchain이 자동 확보 가능) |
| Node.js | Gradle이 관리 (`frontend-react/.gradle/nodejs`) |
| Docker | Compose v2 |
| PostgreSQL | 15 (컨테이너로 제공) |

## D-1 로컬 통합 실행 (Docker Compose) `확인됨`

가장 간단한 경로입니다.

```bash
# 1. .env 준비 (선택 — 없어도 뜨지만 메일이 로그로만 남음)
cp .env.example .env      # PowerShell: Copy-Item .env.example .env

# 2. 빌드 + 기동
docker compose up -d --build

# 3. 확인
curl -i http://localhost:8080/
curl -i http://localhost:8080/favicon.svg
curl -i http://localhost:8080/api/boards
```

셋 다 200이어야 합니다(`PRD.md` 8.9).

### 무엇이 일어나는가 `확인됨`

```
docker compose up --build
  └─ Dockerfile 빌드 스테이지 (temurin:25-jdk)
       ├─ Gradle wrapper·설정 COPY (레이어 캐시용)
       ├─ 소스 COPY
       └─ ./gradlew build -x test
            ├─ :frontend-react  npm ci + vite build → build/dist
            ├─ :backend-springboot processResources
            │     └─ frontendAssets → src/main/resources/static/
            └─ bootWar → backend-springboot-0.0.1-SNAPSHOT.war
  └─ 런타임 스테이지 (temurin:25-jre)
       └─ app.war COPY, /app/uploads 생성
  └─ postgres 컨테이너 기동
  └─ app 기동 → Flyway V1~V4 실행 → 서비스 시작
```

## D-2 로컬 분리 실행 (개발) `확인됨`

프론트엔드 핫 리로드가 필요할 때.

```bash
# 1. DB 만 컨테이너로
docker compose up -d postgres

# 2. .env 의 PGSQL_HOST 확인
#    같은 PC 면 localhost, 다른 PC 의 Docker 면 그 PC 의 IP

# 3. 백엔드
./gradlew :backend-springboot:bootRun     # :8080

# 4. 프론트엔드 (별도 터미널)
cd frontend-react
npm run dev                                # :5173, /api 를 8080 으로 프록시
```

브라우저는 `http://localhost:5173`으로 접속합니다.

## D-3 운영 배포 `제안`

**운영 환경이 정의되어 있지 않으므로 아래는 초안입니다.**

### 배포 전 필수 확인

이 목록을 통과하지 않으면 배포하면 안 됩니다.

| # | 확인 | 이유 |
|---|---|---|
| 1 | `CAPTCHA_MODE=remote` 이고 엔드포인트·시크릿이 채워져 있는가 | `fake`면 CAPTCHA가 없는 것과 같음 |
| 2 | `APP_BASE_URL`이 외부 접근 가능한 주소인가 | 메일 링크가 `localhost`를 가리킴 |
| 3 | `MAIL_DEBUG=false` 인가 | SMTP 인증 정보가 로그에 남음 |
| 4 | DB 비밀번호가 `board_password`가 아닌가 | 기본값 노출 |
| 5 | TLS 종단이 구성되어 있는가 | 세션 쿠키·비밀번호 평문 전송 |
| 6 | `.env`가 커밋되지 않았는가 (`git check-ignore -v .env`) | 비밀값 유출 |
| 7 | DB·업로드 볼륨 백업 수단이 있는가 | 손실 시 복구 불가 |
| 8 | 5432가 외부에 노출되지 않는가 | compose 기본값이 노출함 |

1번과 2번이 가장 자주 빠집니다.

### 알려진 미비 사항 `미결정`

운영 배포 전에 해결하거나 감수해야 할 것들입니다.

| 항목 | 영향 |
|---|---|
| HTTPS 강제 설정 없음 | 프록시에 의존. 앱은 평문도 받음 |
| 쿠키 `Secure`·`SameSite` 없음 | 프록시가 TLS를 해도 쿠키가 보호되지 않음 |
| `show-sql: true` 하드코딩 | 로그 폭증 + 개인정보 노출 |
| 헬스체크 없음 | 오케스트레이터가 상태를 못 봄 |
| 파일 업로드에 인증 없음 | 디스크 고갈 공격 가능 |
| 인메모리 세션 | 재시작 시 전원 로그아웃, 이중화 불가 |

### 제안 토폴로지

```
인터넷 :443
   ▼
리버스 프록시 (TLS 종단)
   │  X-Forwarded-Proto, X-Forwarded-For 전달
   ▼ :8080
app 컨테이너 × 1
   ├── PostgreSQL (관리형 권장)
   └── 업로드 볼륨
```

**app을 1대로 두어야 합니다.** 세션과 업로드 파일이 인스턴스 로컬에 있어
2대 이상에서는 동작이 깨집니다.

### 환경 변수 예 `제안`

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://db.internal:5432/board_db
SPRING_DATASOURCE_USERNAME=board_user
SPRING_DATASOURCE_PASSWORD=<강한 비밀번호>

APP_BASE_URL=https://board.example.com
APP_STORAGE_ROOT=/app/uploads

MAIL_SMTP_HOST=<전용 발송 서비스>
MAIL_USERNAME=<계정>
MAIL_PASSWORD=<비밀번호>
MAIL_FROM_ADMIN=no-reply@example.com
MAIL_DEBUG=false

CAPTCHA_MODE=remote
CAPTCHA_ENDPOINT=<provider 검증 URL>
CAPTCHA_SECRET=<provider 시크릿>
```

`CAPTCHA_ENDPOINT` 연동 시 주의: 현재 구현은 **JSON 본문**으로 보냅니다.
reCAPTCHA·hCaptcha는 form 인코딩을 요구하므로 어댑터 수정이 필요할 수 있습니다.
→ [../api/integration-contracts.md](../api/integration-contracts.md) IC-002

## 마이그레이션 처리 `확인됨`

**앱 기동과 동시에 Flyway가 자동 실행됩니다.** 별도 단계가 없습니다.

| 상황 | 결과 |
|---|---|
| 새 마이그레이션 있음 | 자동 적용 |
| 체크섬 불일치 | **기동 실패** (DB는 삭제되지 않음) |
| 스키마와 엔티티 불일치 | `ddl-auto: validate`가 기동 실패 |

검증 실패가 삭제로 이어지지 않는 것이 핵심 안전장치입니다
(`clean-disabled: true`).

대응: [runbooks/RB-002-flyway-validation-failure.md](runbooks/RB-002-flyway-validation-failure.md)

## 배포 후 확인 `제안`

```bash
# 1. 기본 응답
curl -i https://board.example.com/
curl -i https://board.example.com/api/boards

# 2. SPA 딥링크 (index.html 폴백)
curl -i https://board.example.com/boards/free

# 3. CSRF 쿠키가 내려오는가
curl -i https://board.example.com/api/boards | grep -i xsrf-token

# 4. 로그에서 메일 발송기 확인
docker compose logs app | grep -i "SMTP"
#   "SMTP 메일 발송을 사용합니다"  → 정상
#   "SMTP 설정이 비어 있어..."      → 폴백 상태. 메일이 안 감
```

4번이 중요합니다. `MailConfig`가 기동 시 어느 발송기를 골랐는지 로그로 남깁니다.

## 롤백 `제안`

**롤백 절차가 정의되어 있지 않습니다.**

이미지를 이전 버전으로 되돌리는 것은 가능하지만,
**마이그레이션은 자동으로 되돌아가지 않습니다.**

| 상황 | 대응 |
|---|---|
| 코드만 문제 | 이전 이미지로 교체 |
| 마이그레이션이 스키마를 바꿈 | 구버전 앱이 `validate`에 실패할 수 있음 |

따라서 **되돌리기 쉬운 마이그레이션을 선호**해야 합니다
(컬럼 추가는 안전, 컬럼 삭제는 위험).
→ [../data/migration-policy.md](../data/migration-policy.md)

버전이 `0.0.1-SNAPSHOT` 고정이라 이미지 태그로 버전을 구분할 수 없는 것도
롤백을 어렵게 만듭니다. `미결정`

## 백업 `제안`

```bash
# DB 덤프
docker exec board-postgres pg_dump -U board_user board_db > backup-$(date +%F).sql

# 업로드 파일
docker run --rm \
  -v <프로젝트명>_board_uploads:/data \
  -v "$PWD":/backup alpine \
  tar czf /backup/uploads-$(date +%F).tar.gz -C /data .
```

볼륨 이름의 접두사는 Compose 프로젝트 이름(기본값은 디렉터리 이름)입니다.
`docker volume ls`로 실제 이름을 확인하세요.

## 문제 해결

| 증상 | 문서 |
|---|---|
| 메일이 오지 않음 | [runbooks/RB-001-mail-not-sent.md](runbooks/RB-001-mail-not-sent.md) |
| 기동 시 Flyway 오류 | [runbooks/RB-002-flyway-validation-failure.md](runbooks/RB-002-flyway-validation-failure.md) |
| 그 외 | [runbooks/README.md](runbooks/README.md) |

## 관련 문서

- [environments.md](environments.md) — 환경 변수 전체
- [../architecture/deployment.md](../architecture/deployment.md) — 토폴로지
- [ci-cd.md](ci-cd.md) — 자동화 제안
- [../../security/secrets-management.md](../../security/secrets-management.md)
