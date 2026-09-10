# 환경 구성

> 상태: `확인됨` — `application.yml`, `.env.example`, `docker-compose.yml`, `build.gradle`에서 도출.

## 환경 목록 `확인됨`

Spring profile을 쓰지 않습니다. `application-dev.yml` 같은 파일이 없고,
**환경 차이를 전부 환경 변수로 흡수**합니다.

| 환경 | 실행 방법 | 설정 출처 |
|---|---|---|
| 로컬 분리 실행 | `bootRun` + `npm run dev` | 저장소 루트 `.env` |
| 로컬 통합 | `docker compose up` | `.env` → compose → 컨테이너 env |
| 운영 | 미정의 `미결정` | 환경 변수 전제 |

**운영 환경이 정의되어 있지 않습니다.** 배포 대상, TLS 종단, 시크릿 주입 방식이
저장소 어디에도 없습니다.

## 설정 우선순위 `확인됨`

```
낮음  1. application.yml 의 기본값     ${PGSQL_HOST:192.168.29.124}
      2. 저장소 루트 .env               PGSQL_HOST=192.168.1.10
높음  3. OS 환경 변수 / 컨테이너 env    SPRING_DATASOURCE_URL=...
```

`application.yml` 주석에 명시되어 있습니다 —
"같은 이름의 OS 환경 변수가 있으면 그쪽이 이긴다(운영/컨테이너 배포 경로)".

## `.env` 읽기 방식 `확인됨`

```yaml
spring:
  config:
    import:
      - optional:file:./.env[.properties]
      - optional:file:../.env[.properties]
```

| 요소 | 의미 |
|---|---|
| `optional:` | 파일이 없어도 앱이 뜸 |
| `[.properties]` | **dotenv가 아니라 .properties 문법으로 파싱** |
| 경로 2개 | 저장소 루트 실행과 모듈 디렉터리 실행 모두 지원 |

`bootRun`은 추가로 절대 경로를 넘깁니다(`backend-springboot/build.gradle`) —
작업 디렉터리와 무관하게 같은 파일 하나를 봅니다.

**테스트에는 넘기지 않습니다.** 테스트가 개발자 로컬 `.env` 값에 흔들리면 안 되기 때문입니다.

### `.properties` 문법의 함정 `확인됨`

`.env.example`에 경고가 적혀 있습니다.

| 함정 | 결과 |
|---|---|
| 값을 따옴표로 감쌈 | `MAIL_PASSWORD="abcd"` → 따옴표까지 값이 됨 |
| 역슬래시 사용 | `\`가 이스케이프 문자. 경로는 `/` 또는 `\\` |
| **값에 한글** | ISO-8859-1로 읽혀 깨짐. 비밀번호는 ASCII로 |
| **줄을 두고 값만 비움** | `PGSQL_HOST=` → 빈 문자열이 호스트가 됨 |

마지막이 가장 자주 사고를 냅니다.
기본값을 쓰려면 **줄을 통째로 지워야** 합니다.

## 환경 변수 전체 `확인됨`

### 데이터베이스

| 변수 | 기본값 | 용도 |
|---|---|---|
| `PGSQL_HOST` | `192.168.29.124` | DB 호스트 |
| `PGSQL_PORT` | `5432` | DB 포트 |
| `SPRING_DATASOURCE_URL` | — | **URL 전체를 덮어씀** (컨테이너용) |
| `SPRING_DATASOURCE_USERNAME` | `board_user` (yml) | |
| `SPRING_DATASOURCE_PASSWORD` | `board_password` (yml) | **평문** `미결정` |

계정과 비밀번호가 `application.yml`에 **평문으로 하드코딩**되어 있습니다.
`.env`로 빠져 있지 않습니다.

### 메일

| 변수 | 기본값 | 비고 |
|---|---|---|
| `MAIL_SMTP_HOST` | (빈 값) | 비면 로그 전용 폴백 |
| `MAIL_SMTP_PORT` | `465` | 465=SSL, 587=STARTTLS |
| `MAIL_SMTP_SSL` | `true` | |
| `MAIL_SMTP_STARTTLS` | `false` | |
| `MAIL_USERNAME` | (빈 값) | 비면 로그 전용 폴백 |
| `MAIL_PASSWORD` | (빈 값) | 네이버는 앱 비밀번호 |
| `MAIL_FROM_ADMIN` | `no-reply@localhost` | 발신자 |
| `MAIL_FROM` | — | `MAIL_FROM_ADMIN`의 대체 |
| `MAIL_DEBUG` | `false` | **켜면 인증 정보가 로그에 남음** |

`app.mail.from`은 `${MAIL_FROM_ADMIN:${MAIL_FROM:no-reply@localhost}}`로
2단계 폴백입니다.

### 애플리케이션

| 변수 | 기본값 | 용도 |
|---|---|---|
| `APP_BASE_URL` | `http://localhost:8080` | **메일 링크의 기준 주소** |
| `APP_STORAGE_ROOT` | `./uploads` (yml은 `app.storage.root`) | 업로드 경로 |

`APP_BASE_URL`을 바꾸지 않으면 메일의 인증 링크가 `localhost:8080`을 가리켜
사용자가 접속할 수 없습니다. **배포 시 가장 흔한 실수입니다.**

### CAPTCHA

| 변수 | 기본값 | 용도 |
|---|---|---|
| `CAPTCHA_MODE` | `fake` | `fake` / `remote` |
| `CAPTCHA_ENDPOINT` | (빈 값) | provider URL |
| `CAPTCHA_SECRET` | (빈 값) | provider 비밀키 |
| `CAPTCHA_EXPECTED_TOKEN` | `dev-captcha` | `fake` 모드 통과 토큰 |
| `CAPTCHA_TIMEOUT` | `3s` | |

## 환경별 실제 값 `확인됨`

| 항목 | 로컬 분리 | Docker Compose | 운영(제안) |
|---|---|---|---|
| 프론트엔드 | Vite :5173 | WAR 내장 | WAR 내장 |
| API | :8080 | :8080 | :8080 (프록시 뒤) |
| DB 주소 | `.env` `PGSQL_HOST` | `postgres:5432` | 관리형 DB |
| 업로드 경로 | `./uploads` | `/app/uploads` (볼륨) | 볼륨 또는 오브젝트 스토리지 |
| 메일 | 미설정 → 로그 | `.env` 전달 | 전용 발송 서비스 |
| CAPTCHA | `fake` | `fake` | **`remote` 필수** |
| `APP_BASE_URL` | `localhost:8080` | `localhost:8080` | **실제 도메인** |
| `MAIL_DEBUG` | `false` | `false` | `false` |
| `show-sql` | `true` | `true` | **`false` 여야 함** `미결정` |

### `show-sql`이 모든 환경에서 `true` `확인됨

```yaml
jpa:
  show-sql: true
  properties:
    hibernate:
      format_sql: true
```

환경 변수로 뺄 수 없게 하드코딩되어 있습니다.
운영에서 다음 문제가 생깁니다.

- 로그 양이 폭증
- SQL 파라미터에 이메일·이름 등 개인정보가 포함될 수 있음
- 성능 저하

`${SHOW_SQL:false}`로 바꾸는 것을 권합니다. `제안`

## 개발 환경 준비 `확인됨`

```powershell
# 1. .env 생성 (1회)
Copy-Item .env.example .env

# 2. .env 편집 — 최소한 PGSQL_HOST
#    같은 PC 의 Docker 면 localhost, 다른 PC 면 그 PC 의 IP

# 3. DB 구동
docker compose up -d postgres

# 4. 백엔드
./gradlew :backend-springboot:bootRun

# 5. 프론트엔드 (선택 — 핫 리로드가 필요할 때)
cd frontend-react
npm run dev
```

`.env` 없이도 앱은 뜹니다(`optional:`). 다만 기본 DB 주소가
`192.168.29.124`라 그 주소에 DB가 없으면 기동이 실패합니다.

### `.env` 없이 동작하는 것 / 안 하는 것 `확인됨`

| 항목 | `.env` 없이 |
|---|---|
| 앱 기동 | 기본 DB 주소가 맞으면 동작 |
| 회원가입 | 동작 (`CAPTCHA_MODE=fake` 기본값, 토큰 `dev-captcha`) |
| 메일 발송 | **로그로만** — 실제 발송 안 됨 |
| 이메일 인증 | 로그에서 링크를 복사해 수동 접속 |

`UI.md`의 "개발 환경에서의 이메일 인증 확인" 절이 이 방법을 설명합니다.

## 검증 `확인됨`

`plan.md`의 검증 기준에서 왔습니다.

```bash
# .env 가 커밋 대상이 아닌지
git check-ignore -v .env

# 다른 PC 의 DB 로 전환되는지 — PGSQL_HOST 만 바꾸고 재기동
./gradlew :backend-springboot:bootRun

# 컨테이너 정상 동작
docker compose up -d --build
curl -i http://localhost:8080/
curl -i http://localhost:8080/favicon.svg
curl -i http://localhost:8080/api/boards
```

셋 다 200이어야 합니다(`PRD.md` 8.9).

## 없는 것 `미결정`

| 항목 | 영향 |
|---|---|
| Spring profile | 환경별 설정 파일 분리 불가 |
| 스테이징 환경 | 운영 전 검증 단계 없음 |
| 시크릿 관리 도구 | `.env` 평문 파일에만 의존 |
| 설정 검증 | 잘못된 값을 기동 시 잡아내지 않음 |
| 헬스체크 엔드포인트 | Actuator 없음 |

### 설정 검증이 없는 결과 `확인됨`

`CAPTCHA_MODE=fake`인 채로 운영에 나가도 아무도 경고하지 않습니다.
`APP_BASE_URL`이 `localhost`여도 마찬가지입니다.

기동 시 위험한 설정 조합을 경고하는 코드를 넣는 것이 안전합니다. `제안`
`MailConfig`가 SMTP 미설정 시 `log.warn`을 남기는 것과 같은 방식입니다.

## 관련 문서

- [deployment-guide.md](deployment-guide.md) — 배포 절차
- [../architecture/deployment.md](../architecture/deployment.md) — 토폴로지
- [../../security/secrets-management.md](../../security/secrets-management.md) — 비밀값
- [../api/integration-contracts.md](../api/integration-contracts.md) — 외부 연동 설정
