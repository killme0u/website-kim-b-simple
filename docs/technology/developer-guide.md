# 개발자 가이드

> 상태: `확인됨` — 실제 빌드 설정·코드 구조 기준.

## 처음 시작하기 `확인됨`

```powershell
# 1. .env 준비 (1회)
Copy-Item .env.example .env

# 2. .env 에서 PGSQL_HOST 를 자기 환경에 맞게 수정
#    같은 PC 의 Docker → localhost
#    다른 PC 의 Docker → 그 PC 의 IP

# 3. DB 구동
docker compose up -d postgres

# 4. 백엔드
./gradlew :backend-springboot:bootRun

# 5. 프론트엔드 (별도 터미널)
cd frontend-react
npm run dev
```

접속: `http://localhost:5173` (Vite dev server, `/api`를 8080으로 프록시)

### `.env` 없이도 되는가 `확인됨`

앱은 뜹니다(`optional:file:`). 다만:

| 항목 | `.env` 없이 |
|---|---|
| DB 주소 | 기본값 `192.168.29.124:5432` — 맞지 않으면 기동 실패 |
| 메일 | **로그로만 남음** |
| CAPTCHA | `fake` 모드, 토큰 `dev-captcha` |

### 이메일 인증을 로컬에서 통과하기 `확인됨`

SMTP를 설정하지 않았다면 로그에서 링크를 꺼냅니다.

```bash
# bootRun 콘솔 또는
docker compose logs app | grep -A 30 "verify-email"
```

HTML 본문 안의 `http://localhost:8080/verify-email?token=...`을 브라우저에 붙여넣습니다.

## `.env` 작성 시 함정 `확인됨`

**dotenv가 아니라 `.properties` 문법으로 읽힙니다.**

| 하지 말 것 | 이유 |
|---|---|
| `MAIL_PASSWORD="abcd"` | 따옴표까지 값이 됨 |
| `PATH=C:\dir` | `\`가 이스케이프 문자. `/` 또는 `\\` |
| 값에 한글 | ISO-8859-1로 읽혀 깨짐 |
| `PGSQL_HOST=` (값만 비움) | 빈 문자열이 호스트가 됨. **줄을 통째로 지워야** 기본값 |

## 코드 어디를 열 것인가 `확인됨`

기능별 시작점입니다.

| 하려는 일 | 시작 파일 |
|---|---|
| 게시판 권한 규칙 변경 | `board/domain/Board.java` |
| 게시글 수정·삭제 권한 | `post/domain/Post.java` (`checkEditable`) |
| 조회수 정책 | `post/application/PostService.java` (`getPost`) |
| 가입 흐름 | `member/application/SignupService.java` |
| 이메일 인증·비밀번호 재설정 | `member/application/VerificationService.java` |
| 메일 내용·템플릿 | `member/application/SignupMailListener.java`, `resources/templates/mail/` |
| 오류 → HTTP 매핑 | `common/GlobalExceptionHandler.java` |
| 인증·경로 권한 | `config/SecurityConfig.java` |
| 파일 저장·다운로드 | `attachment/application/FileStorageService.java` |
| CAPTCHA 연동 | `captcha/adapter/out/ConfiguredCaptchaVerifier.java` |
| 화면 라우트 | `frontend-react/src/App.tsx` |
| API 클라이언트·CSRF | `frontend-react/src/lib/axios.ts` |
| 로그인 상태 | `frontend-react/src/store/authStore.ts` |

## 패키지 구조 규칙 `확인됨`

도메인이 최상위, 그 아래 계층입니다.

```
<도메인>/
├── adapter/in/web/     컨트롤러, DTO
├── adapter/out/<외부>/  외부 연동 구현
├── application/        서비스, 포트 인터페이스, 이벤트
└── domain/             엔티티, 리포지토리
```

의존 방향: `adapter.in` → `application` → `domain`, 그리고 `adapter.out` → `application`(포트 구현)

**새 기능을 추가할 때 기술 계층이 아니라 도메인에 넣으세요.**
`controller/` 같은 디렉터리를 만들지 않습니다.

## 자주 하는 작업

### 1. 새 API 추가 `제안`

```
1. domain/    엔티티·규칙 (필요하면)
2. application/  서비스 메서드 — @Transactional 경계
3. adapter/in/web/dto/  Command·Response DTO
4. adapter/in/web/  컨트롤러
5. 권한 판정 확인 ← 가장 중요
6. openapi.yaml 갱신
7. features/api-behavior.md 갱신
```

**5번을 빠뜨리면 무방비로 열립니다.** `SecurityConfig`가 대부분 `permitAll`이므로
도메인에서 판정하지 않으면 아무도 막지 않습니다.
실제로 `POST /api/files`가 이 상태입니다.

체크리스트: [api/api-guidelines.md](api/api-guidelines.md) 마지막 절

### 2. 스키마 변경 `확인됨`

```
1. V{n}__{설명}.sql 작성 (기존 파일 수정 금지)
2. 엔티티 필드 추가
3. 기동해서 validate 통과 확인
4. data/data-model.md, data/erd.md 갱신
```

`ddl-auto: validate`이므로 **엔티티만 고치면 앱이 뜨지 않습니다.**

**절대 하지 말 것**: 적용된 마이그레이션 수정, `flyway clean`,
`clean-disabled`를 `false`로 변경.
→ [data/migration-policy.md](data/migration-policy.md)

### 3. 게시판 정책 변경 `확인됨`

코드를 고치지 않습니다. 마이그레이션 한 줄입니다.

```sql
-- V5__allow_comment_on_free.sql
UPDATE board SET allows_comment = TRUE WHERE slug = 'free';
```

정책을 데이터로 표현한 덕분입니다(`V4`가 같은 방식).

### 4. 외부 연동 추가 `제안`

`MailSenderPort` 패턴을 따릅니다.

```
1. application/  포트 인터페이스 정의
2. adapter/out/  구현
3. adapter/out/  @ConfigurationProperties 설정 바인딩
4. config/       빈 등록 + 미설정 시 폴백
5. 타임아웃 지정
6. 실패 시 로그 남기기 ← CAPTCHA 의 교훈
7. .env.example 에 양식 추가
```

6번을 빠뜨린 것이 `ConfiguredCaptchaVerifier`입니다 —
모든 예외를 `false`로 삼켜 원인을 알 수 없습니다.

### 5. 버그 수정 `확인됨`

이 프로젝트의 관행입니다.

```
1. 원인 파악
2. 수정
3. 회귀 테스트 작성 ← 한국어 @DisplayName 에 "왜"까지
4. todo.md 갱신 (원인·조치·회귀 테스트·관련 문서)
5. 영향받는 문서 갱신
```

`todo.md`의 기존 항목이 좋은 예시입니다 —
원인, 조치, 함께 고친 것, 회귀 테스트, 문서까지 적혀 있습니다.

## 빌드 `확인됨`

```bash
# 전체 (프론트엔드 자동 포함)
./gradlew build

# 백엔드 테스트만
./gradlew :backend-springboot:test

# 프론트엔드
cd frontend-react
npm run lint     # oxlint
npm run build    # tsc -b && vite build

# 컨테이너
docker compose up -d --build
```

`./gradlew build`가 프론트엔드를 자동으로 먼저 빌드합니다
(Gradle 산출물 공유, ADR-001).

### 실행 아티팩트 주의 `확인됨`

`war` 플러그인 때문에 `build/libs`에 세 파일이 생깁니다.
실행 가능한 것은 **`-plain`이 없는 `.war` 하나뿐**입니다.

```
backend-springboot-0.0.1-SNAPSHOT.war         ← 실행 가능
backend-springboot-0.0.1-SNAPSHOT-plain.war   ← 불가
backend-springboot-0.0.1-SNAPSHOT-plain.jar   ← 불가
```

## Tailwind 4 주의 `확인됨`

`tailwind.config.js`와 `postcss.config.js`가 **없습니다** (`667047a`에서 삭제).

설정 위치:
- `frontend-react/src/index.css`의 `@import 'tailwindcss'`
- `vite.config.ts`의 `@tailwindcss/vite` 플러그인

설정 파일을 찾다가 없다고 새로 만들지 마세요.

## `Dockerfile` COPY 목록 `확인됨`

프론트엔드에 **새 설정 파일을 추가하면 `Dockerfile`에도 추가**해야 합니다.

현재 COPY 대상:
```
frontend-react/src, vite.config.ts, tsconfig*.json, index.html, public/
```

이 프로젝트에서 실제로 두 번 문제가 됐습니다.
로컬 빌드는 되는데 `docker compose up --build`가 깨지는 증상으로 나타납니다.

## 코드 스타일 `확인됨`

기존 코드에서 관찰되는 관행입니다.

| 항목 | 관행 |
|---|---|
| 엔티티 생성 | 정적 팩토리 (`Member.pending`, `Post.guest`) |
| 기본 생성자 | `@NoArgsConstructor(access = PROTECTED)` |
| 연관관계 | `@ManyToOne(fetch = LAZY)`. `@OneToMany` 안 씀 |
| 의존성 주입 | `@RequiredArgsConstructor` + `final` |
| 주석 | **왜 그렇게 했는지**를 적음. 무엇을 하는지는 코드로 |
| 오류 메시지 | 한국어 |
| 테스트 이름 | 한국어 `@DisplayName`에 이유까지 |

### 주석 관행이 특히 중요합니다 `확인됨`

이 프로젝트의 주석은 **함정과 그 배경**을 기록합니다.

```java
// spa() = CookieCsrfTokenRepository.withHttpOnlyFalse() + SpaCsrfTokenRequestHandler.
// 매 요청마다 XSRF-TOKEN 쿠키를 실제로 내려주고, 헤더로 들어온 원본 토큰을 그대로 검증한다.
// (기본 XorCsrfTokenRequestAttributeHandler는 마스킹된 토큰을 기대하므로 ... 403이 난다.)
```

다음 사람이 같은 함정에 빠지지 않게 하는 것이 목적입니다. 이 관행을 유지하세요.

## 알아두면 좋은 함정 `확인됨`

| 함정 | 증상 |
|---|---|
| 엔티티만 고치고 마이그레이션 안 씀 | 기동 실패 (`validate`) |
| 적용된 마이그레이션 수정 | 기동 실패 (체크섬) |
| `.env`에 한글 | 값이 깨짐 |
| `PGSQL_HOST=` (값만 비움) | 접속 실패 |
| `SecurityConfig`만 보고 권한 판단 | 대부분 `permitAll` — 도메인을 봐야 함 |
| `V2`만 보고 게시판 정책 판단 | `V4`가 덮어씀 |
| 없는 리소스 조회 | 404가 아니라 **500** |
| 프론트엔드 설정 파일 추가 | `Dockerfile` COPY 누락 |

## 문서 갱신 의무 `제안`

코드를 바꾸면 함께 고칠 문서입니다.

| 변경 | 갱신 대상 |
|---|---|
| API 추가·변경 | `api/openapi.yaml`, `features/api-behavior.md` |
| 스키마 변경 | `data/data-model.md`, `data/erd.md` |
| 권한 규칙 변경 | `business/policy-rules.md`, `security/access-control.md` |
| 상태 전이 변경 | `features/state-machines.md` |
| 오류 처리 변경 | `features/error-policy.md` |
| 아키텍처 결정 | `technology/adr/` 새 ADR |
| `미결정` 항목 확정 | 해당 문서 + `PRD.md` 10장 |

## 관련 문서

- [architecture-overview.md](architecture-overview.md) — 전체 구조
- [architecture/components.md](architecture/components.md) — 패키지 상세
- [api/api-guidelines.md](api/api-guidelines.md) — API 규약
- [data/migration-policy.md](data/migration-policy.md) — 스키마 변경
- [testing-strategy.md](testing-strategy.md) — 테스트
- [infrastructure/environments.md](infrastructure/environments.md) — 설정
