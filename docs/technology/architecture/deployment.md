# 배포 토폴로지

> 상태: `확인됨` — `docker-compose.yml`, `Dockerfile`, `application.yml`에서 도출.
> 운영 배포 구성(TLS, 리버스 프록시)은 저장소에 없어 `제안`입니다.

## 현재 지원되는 토폴로지 `확인됨`

### T-1 로컬 분리 실행 (개발)

```
개발자 PC
├── Vite dev server  :5173   ──proxy /api──┐
├── Spring bootRun   :8080  ◄──────────────┘
└── .env  (PGSQL_HOST / PGSQL_PORT)
              │
              ▼
      Docker PostgreSQL  :5432
      (같은 PC 또는 다른 PC)
```

특징: 프론트엔드 핫 리로드가 살아 있습니다.
DB는 `.env`의 `PGSQL_HOST`로 어디든 가리킬 수 있습니다.

### T-2 Docker Compose 단일 호스트 (권장)

```
호스트
└── docker compose
    ├── app       :8080 → 호스트 :8080
    │   └── volume board_uploads → /app/uploads
    └── postgres  :5432 → 호스트 :5432
        └── volume board_data
```

`docker compose up -d --build` 한 줄로 뜹니다.
프론트엔드가 WAR 안에 들어가므로 포트가 하나입니다.

### T-3 원격 Docker 호스트 `확인됨`

`.env`의 `PGSQL_HOST`를 다른 PC의 IP로 바꾸면 됩니다.
`PRD.md` 8.7이 이 시나리오를 다룹니다 — 회사·집·학원에서 각각 다른 Docker 호스트를 씀.

```
작업 PC (bootRun)  ──JDBC──►  다른 PC의 Docker PostgreSQL
```

코드 수정 없이 `.env` 두 줄만 바꿉니다.

## 설정 우선순위 `확인됨`

같은 값을 여러 곳에서 줄 수 있고, 아래가 이깁니다.

```
1. application.yml 의 기본값        ${PGSQL_HOST:192.168.29.124}
2. 저장소 루트 .env                 PGSQL_HOST=...
3. OS 환경 변수 / 컨테이너 env      SPRING_DATASOURCE_URL=...
```

컨테이너에서는 `SPRING_DATASOURCE_URL`이 **URL 전체를 덮어씁니다.**
`PGSQL_HOST`가 아니라 완성된 URL을 주므로 `.env`의 값과 무관해집니다.

### `.env` 읽기 방식의 함정 `확인됨`

```yaml
spring:
  config:
    import:
      - optional:file:./.env[.properties]
      - optional:file:../.env[.properties]
```

| 사실 | 결과 |
|---|---|
| `optional:` | `.env`가 없어도 앱이 뜸 |
| `[.properties]` | **dotenv가 아니라 .properties 문법** |
| 두 경로 | 저장소 루트 실행과 모듈 디렉터리 실행 모두 지원 |

`.properties`이므로 주의할 점 (`.env.example`에 기록됨):

- 값을 따옴표로 감싸면 따옴표까지 값이 됩니다
- 역슬래시 `\`는 이스케이프 문자입니다. 경로는 `/` 또는 `\\`
- **ISO-8859-1로 읽히므로 값에 한글을 쓰면 안 됩니다**
- 줄을 지우면 기본값이 쓰이지만, 줄을 두고 값만 비우면 **빈 문자열**이 됩니다

마지막 항목이 가장 자주 사고를 냅니다.
`PGSQL_HOST=`로 두면 호스트가 빈 문자열이 되어 접속이 깨집니다.

## 스토리지 `확인됨`

| 환경 | 경로 | 지속성 |
|---|---|---|
| 로컬 | `./uploads` (`app.storage.root` 기본값) | 작업 디렉터리에 종속 |
| 컨테이너 | `/app/uploads` (`APP_STORAGE_ROOT`) | `board_uploads` 볼륨 |

`FileStorageService.init()`이 기동 시 디렉터리를 만듭니다.
Dockerfile이 `chmod 777 /app/uploads`로 권한을 엽니다.

**`chmod 777`은 필요 이상으로 넓습니다.** 컨테이너가 root로 실행되므로
소유권만 맞추면 되는데 모든 권한을 열었습니다. `미결정`

## 데이터베이스 마이그레이션 `확인됨`

기동 시 Flyway가 자동 실행됩니다.

```yaml
flyway:
  enabled: true
  baseline-on-migrate: true
  clean-disabled: true
  clean-on-validation-error: false
```

| 설정 | 효과 |
|---|---|
| `baseline-on-migrate` | 기존 DB에 이력 테이블이 없어도 시작 가능 |
| `clean-disabled: true` | **스키마 전체 삭제를 차단** |
| `clean-on-validation-error: false` | 검증 실패 시 삭제하지 않음 |

이전에는 `clean-disabled: false` + `clean-on-validation-error: true`였습니다.
그 조합에서는 **마이그레이션 검증이 실패하면 앱이 뜨는 것만으로 대상 DB가 통째로 지워집니다.**
PC마다 다른 DB를 보는 구성에서는 특히 위험합니다.

Hibernate는 `ddl-auto: validate`라 스키마를 만들지 않습니다.
스키마의 유일한 출처는 Flyway 마이그레이션입니다.
→ [../data/migration-policy.md](../data/migration-policy.md)

## 운영 배포에 없는 것 `미결정`

| 항목 | 상태 | 영향 |
|---|---|---|
| TLS / HTTPS | 설정 없음 | 세션 쿠키·비밀번호가 평문 전송 |
| 리버스 프록시 구성 | 문서·코드 없음 | TLS 종단 지점 불명 |
| 쿠키 `Secure`·`SameSite` | 미지정 | 프록시가 있어도 쿠키가 보호되지 않음 |
| 헬스체크 | Actuator 없음 | compose·오케스트레이터가 상태를 못 봄 |
| `depends_on` 조건 | `condition` 없음 | postgres가 준비되기 전에 app이 뜰 수 있음 |
| 리소스 제한 | 없음 | 메모리 폭주 시 호스트 전체 영향 |
| 로그 수집 | stdout만 | 컨테이너 재시작 시 유실 |
| 백업 | 없음 | `board_data` 볼륨 손실 = 전체 손실 |
| 시크릿 관리 | compose 평문 | DB 자격 증명 노출 |

### `depends_on`의 한계 `확인됨`

```yaml
depends_on:
  - postgres
```

이것은 **컨테이너 시작 순서만** 보장하고 PostgreSQL이 접속을 받을 준비가 됐는지는
보지 않습니다. app이 먼저 떠서 Flyway가 접속에 실패하면 기동이 실패합니다.

`condition: service_healthy`와 postgres 헬스체크를 추가하면 해결됩니다. `제안`

## 제안하는 운영 토폴로지 `제안`

```
인터넷
   │ HTTPS :443
   ▼
리버스 프록시 (nginx / Caddy / ALB)
   │  TLS 종단
   │  X-Forwarded-Proto 전달
   │ HTTP :8080
   ▼
app 컨테이너 (1대)
   │
   ├── PostgreSQL (관리형 또는 별도 컨테이너 + 백업)
   └── 업로드 볼륨 (또는 오브젝트 스토리지)
```

app을 1대로 두는 이유: 세션과 업로드 파일이 인스턴스 로컬에 있어
**현재 구조로는 수평 확장이 불가능**합니다.

### 확장하려면 필요한 변경 `제안`

| 대상 | 변경 |
|---|---|
| 세션 | Spring Session + Redis |
| 업로드 | S3 호환 오브젝트 스토리지로 `FileStorageService` 교체 |
| `APP_BASE_URL` | 실제 외부 주소로 (메일 링크가 이 값을 씀) |

`APP_BASE_URL`을 바꾸지 않으면 메일의 인증 링크가 `http://localhost:8080`을 가리켜
사용자가 접속할 수 없습니다. 배포 시 가장 흔한 실수 지점입니다.

## 배포 전 확인 목록 `제안`

`plan.md`의 검증 기준과 위 내용을 합쳤습니다.

- [ ] `APP_BASE_URL`이 외부에서 접근 가능한 주소인가
- [ ] `CAPTCHA_MODE`가 `fake`가 아닌가
- [ ] `MAIL_DEBUG`가 `false`인가 (인증 정보가 로그에 남음)
- [ ] `show-sql`이 꺼져 있는가
- [ ] DB 자격 증명이 기본값(`board_password`)이 아닌가
- [ ] `.env`가 커밋되지 않았는가 (`git check-ignore -v .env`)
- [ ] TLS 종단이 구성되어 있는가
- [ ] `board_data` 볼륨 백업 계획이 있는가
- [ ] `/`, `/favicon.svg`, `/api/boards`가 200을 반환하는가

## 관련 문서

- [../infrastructure/deployment-guide.md](../infrastructure/deployment-guide.md) — 절차
- [../infrastructure/environments.md](../infrastructure/environments.md) — 환경별 설정값
- [containers.md](containers.md) — 컨테이너 구성
- [../../security/secrets-management.md](../../security/secrets-management.md) — 비밀값
