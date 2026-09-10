# 아키텍처 개요

> 상태: `확인됨` — 코드 구조와 빌드 설정에서 도출.

## 한 문장

**단일 Gradle 멀티모듈 저장소에서 React SPA를 빌드해 Spring Boot WAR 안에 넣고,
포트 하나(8080)로 화면과 API를 함께 제공하는 모듈러 모놀리스.**

## 기술 스택 `확인됨`

### 백엔드 (`backend-springboot/build.gradle`)

| 항목 | 버전·값 |
|---|---|
| Java | 25 (toolchain) |
| Spring Boot | 4.1.1 |
| 패키징 | **WAR** (`war` 플러그인 + `providedRuntime` Tomcat) |
| 영속성 | Spring Data JPA + Hibernate |
| 마이그레이션 | Flyway (`flyway-database-postgresql`) |
| 보안 | Spring Security 7 |
| 템플릿 | Thymeleaf (메일 전용) |
| 검증 | Bean Validation |
| 메일 | Spring Boot Mail (JavaMail) |
| 기타 | Lombok, Spring AI Tika Document Reader |

### 프론트엔드 (`frontend-react/package.json`)

| 항목 | 버전 |
|---|---|
| React | 19 |
| TypeScript | ~6.0 |
| 빌드 | Vite 8 |
| 스타일 | Tailwind CSS 4 (`@tailwindcss/vite`) |
| UI | COSS UI (`@base-ui/react` 기반) |
| 라우팅 | React Router 7 |
| 서버 상태 | TanStack Query 5 |
| 클라이언트 상태 | Zustand 5 |
| HTTP | axios |
| 폼 | React Hook Form + Zod |
| 마크다운 | react-markdown |
| 린터 | oxlint |

### 데이터·인프라

| 항목 | 값 |
|---|---|
| DB | PostgreSQL 15 (`postgres:15-alpine`) |
| 컨테이너 | Docker Compose |
| 런타임 이미지 | `eclipse-temurin:25.0.3_9-jre` |

## Tailwind 4의 특이점 `확인됨`

`tailwind.config.js`와 `postcss.config.js`가 **없습니다** (`667047a`에서 삭제).
설정은 두 곳에 있습니다.

- `frontend-react/src/index.css`의 `@import 'tailwindcss'`
- `vite.config.ts`의 `@tailwindcss/vite` 플러그인

`Dockerfile`에 이 사실이 주석으로 적혀 있습니다. 설정 파일을 COPY하려다 없어서
빌드가 깨지는 것을 막기 위한 기록입니다.

## 계층 구조 `확인됨`

백엔드는 **도메인별 패키지 + 헥사고날 계층**입니다.

```
page.sanotehu.board.backend
├── member/          ─┐
│   ├── adapter/in/web/     컨트롤러 + DTO
│   ├── adapter/out/mail/   SMTP·로그 발송기
│   ├── application/        유스케이스, 이벤트, 포트
│   └── domain/             엔티티, 리포지토리
├── board/           │  같은 4계층 구조 반복
├── post/            │
├── comment/         │
├── attachment/      │
├── captcha/         ─┘
├── common/          공통 예외, ApiError, BaseTimeEntity
└── config/          Security, Mail, Async, JPA, SPA 리소스
```

기술 계층(`controller/`, `service/`, `repository/`)이 아니라
**도메인이 최상위**입니다. 한 기능을 고칠 때 한 디렉터리만 열면 됩니다.

### 의존 방향 `확인됨`

```
adapter.in.web  ──►  application  ──►  domain
                          │
                          ▼
                      (port 인터페이스)
                          ▲
                          │
                    adapter.out
```

`application`이 포트 인터페이스를 정의하고 `adapter.out`이 구현합니다.

| 포트 | 구현 | 위치 |
|---|---|---|
| `MailSenderPort` | `SmtpMailSender`, `LoggingMailSender` | `member/adapter/out/mail/` |
| `CaptchaVerifier` | `ConfiguredCaptchaVerifier` | `captcha/adapter/out/` |

구현 선택은 `config/MailConfig`와 `config/CaptchaConfig`가 설정값을 보고 런타임에 합니다.

## 핵심 결정 다섯 가지

### 1. 단일 배포 단위 (ADR-001)

프론트엔드 빌드 산출물이 백엔드 JAR/WAR 안으로 들어갑니다.

```gradle
// backend-springboot/build.gradle
configurations { frontendAssets { canBeConsumed = false; canBeResolved = true } }
dependencies { frontendAssets project(path: ':frontend-react', configuration: 'frontendAssets') }
tasks.named('processResources') { from(configurations.frontendAssets) { into 'static' } }
```

Gradle 산출물 공유로 연결되어 있어, 백엔드를 빌드하면 프론트엔드가 자동으로 먼저 빌드됩니다.
→ [adr/ADR-001-modular-monolith.md](adr/ADR-001-modular-monolith.md)

### 2. PostgreSQL 전용 기능 사용 (ADR-002)

`INSERT ... ON CONFLICT DO NOTHING`(네이티브 쿼리)과 부분 UNIQUE 인덱스를 씁니다.
다른 DB로 옮기려면 이 둘을 다시 써야 합니다.
→ [adr/ADR-002-postgresql.md](adr/ADR-002-postgresql.md)

### 3. 권한 판정을 도메인에 둠

`SecurityConfig`는 경로만 열고 실제 판정은 `Board`·`Post`가 합니다.
비회원 글이라는 요구가 만든 구조입니다.
→ [../features/specifications/FR-001-authentication.md](../features/specifications/FR-001-authentication.md)

### 4. 메일은 커밋 이후 비동기

`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`.
메일 실패가 가입을 되돌리지 않지만, 실패를 사용자가 알 수도 없습니다.

### 5. 세션 쿠키 + CSRF (JWT 아님)

SPA지만 토큰 인증이 아니라 세션을 씁니다.
`csrf().spa()`로 원본 토큰을 쿠키·헤더로 주고받습니다.

## 데이터 흐름 요약 `확인됨`

```
브라우저
  │  (dev) :5173 Vite ──proxy /api──► :8080
  │  (prod) :8080 단일 포트
  ▼
SpaResourceConfig ──► /api/* 가 아니면 정적 리소스, 없으면 index.html
  │
  ▼
Spring Security 필터 체인 (세션, CSRF, 경로 규칙)
  │
  ▼
Controller (@Valid) ──► Service (@Transactional) ──► Domain (권한 판정)
                             │                          │
                             │                          ▼
                             │                      Repository ──► PostgreSQL
                             ▼
                      ApplicationEvent ──(AFTER_COMMIT, @Async)──► MailSenderPort
```

→ [architecture/data-flow.md](architecture/data-flow.md)

## 개발과 운영의 차이 `확인됨`

| 항목 | 개발 | 운영(컨테이너) |
|---|---|---|
| 프론트엔드 | Vite dev server :5173 | WAR 안 정적 리소스 |
| API 연결 | Vite 프록시 | 같은 오리진 |
| DB 주소 | `.env`의 `PGSQL_HOST` | `SPRING_DATASOURCE_URL` 환경 변수 |
| 메일 | 미설정 시 로그 전용 | `.env` 값을 compose가 전달 |
| CAPTCHA | `mode=fake` | `mode=remote` 필요 `미결정` |
| 업로드 경로 | `./uploads` | `/app/uploads` (볼륨) |

CORS 대신 프록시를 쓰는 이유: 개발에서도 **같은 오리진**을 유지해야
쿠키(`JSESSIONID`, `XSRF-TOKEN`)가 정상 동작하기 때문입니다.

## 실행 아티팩트 `확인됨`

`war` 플러그인 때문에 `bootJar`가 아니라 `bootWar`가 만들어집니다.
`build/libs`에 셋이 남는데 실행 가능한 것은 하나뿐입니다.

| 파일 | 실행 |
|---|---|
| `backend-springboot-0.0.1-SNAPSHOT.war` | **가능** (bootWar) |
| `backend-springboot-0.0.1-SNAPSHOT-plain.war` | 불가 |
| `backend-springboot-0.0.1-SNAPSHOT-plain.jar` | 불가 |

`Dockerfile`이 첫 번째만 COPY합니다.

## 알려진 구조적 약점 `미결정`

| 약점 | 영향 |
|---|---|
| 인메모리 세션 | 재시작 시 로그아웃, 수평 확장 불가 |
| 로컬 파일 스토리지 | 인스턴스 간 첨부 공유 불가 |
| 관측 수단 없음 | Actuator·메트릭 부재 |
| 404가 500으로 | 미처리 `NoSuchElementException` |
| CI 없음 | 회귀를 사람이 잡아야 함 |
| `show-sql: true` | 운영에서 로그 과다 |

앞의 둘은 같은 원인입니다 — **상태를 인스턴스 안에 들고 있습니다.**
1대로 운영하는 동안은 문제가 없지만 이중화하는 순간 둘 다 깨집니다.

## 관련 문서

- [architecture/system-context.md](architecture/system-context.md) — C4 L1
- [architecture/containers.md](architecture/containers.md) — C4 L2
- [architecture/components.md](architecture/components.md) — C4 L3
- [architecture/deployment.md](architecture/deployment.md) — 배포 토폴로지
- [quality-attributes.md](quality-attributes.md) — 품질 속성
- [developer-guide.md](developer-guide.md) — 개발 시작하기
