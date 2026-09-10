# 테스트 전략

> 상태: 현황은 `확인됨`(테스트 파일 기준), 전략은 `제안`입니다.

## 현황 `확인됨`

테스트 클래스 5개, 테스트 17개. 위치: `backend-springboot/src/test/java/`

| 클래스 | 테스트 | 대상 |
|---|---|---|
| `SecurityConfigTest` | 4 | CSRF 쿠키·헤더, SPA 셸 접근 |
| `MemberControllerTest` | 4 | 닉네임 중복 확인 정규화 |
| `MailConfigTest` | 3 | SMTP 폴백 판정 |
| `SignupMailListenerTest` | 3 | 메일 발송 경로, 템플릿 |
| `MemberTest` | 3 | 닉네임 정규화 규칙 |

**프론트엔드 테스트는 없습니다.** `package.json`에 `test` 스크립트가 없습니다.

**CI가 없습니다.** 게다가 `Dockerfile`이 `./gradlew build -x test`로
테스트를 건너뛰므로, 테스트가 자동으로 실행되는 곳이 아무 데도 없습니다.

## 현재 테스트의 공통점 `확인됨`

셋 다 **실제로 사고가 났던 지점**입니다.

| 테스트 | 막고 있는 회귀 |
|---|---|
| `SecurityConfigTest` | 로그아웃 403 (`csrf().spa()` 채택 전 버그) |
| `MemberControllerTest`, `MemberTest` | 닉네임 중복 확인 (결정 D5, `5cca82f`) |
| `MailConfigTest` | `.env.example` 복사 직후 SMTP 인증 실패 |

**버그를 고칠 때 회귀 테스트를 함께 쓰는 관행**이 자리잡혀 있습니다.
좋은 출발점입니다. 문제는 아직 사고가 나지 않은 영역이 비어 있다는 것입니다.

## 비어 있는 영역 `확인됨`

| 영역 | 테스트 | 위험 |
|---|---|---|
| 게시글 수정·삭제 권한 | 없음 | **보안 직결** |
| 게시판 읽기·쓰기 권한 | 없음 | `V4` 결정이 회귀할 수 있음 |
| 조회수 중복 방지 | 없음 | 결정 D2의 핵심 |
| 좋아요 토글 | 없음 | |
| CAPTCHA 검증 분기 | 없음 | 스팸 방어의 유일한 관문 |
| 토큰 만료·재사용 거부 | 없음 | 계정 탈취 직결 |
| 계정 상태 전이 | 없음 | |
| 파일 경로 탈출 | 없음 | |
| 오류 → HTTP 매핑 | 없음 | |
| 프론트엔드 전체 | 없음 | |

## 제안하는 계층 `제안`

### 1층 — 도메인 단위 테스트 (가장 저렴, 가장 먼저)

의존성 없이 순수 객체만으로 검증합니다. **비용 대비 효과가 가장 큽니다.**

권한 판정이 도메인에 있어서 가능한 일입니다.

| 대상 | 필요한 것 |
|---|---|
| `Post.checkEditable` | `Optional<Member>`, `PasswordEncoder` |
| `Board.checkReadable` / `checkWritable` | `Optional<Member>` |
| `Member.normalizeNickname` | 없음 (이미 테스트됨) |
| `Member.verifyEmail` | 없음 |
| `VerificationToken.isUsable` | `Clock` 또는 시간 조작 |
| `MediaKind.from` | 없음 |
| `PostResponse.from`의 `isOwner` 계산 | 없음 |

`Post.checkEditable` 하나만으로 **9가지 경우**를 검증할 수 있습니다.

```
로그인 × (관리자 / 작성자 / 타인)
비로그인 × (회원 글 / 비회원 글 정답 비밀번호 / 비회원 글 오답 / 비밀번호 null)
```

`VerificationToken.isUsable`은 `ZonedDateTime.now()`를 직접 부르므로
시간 의존 테스트가 어렵습니다. `Clock` 주입으로 바꾸면 테스트가 쉬워집니다. `제안`
(`PRD.md` 6.2가 `Clock` 빈 사용을 언급하고 있으나 `VerificationToken`에는 적용되지 않았습니다.)

### 2층 — 슬라이스 테스트

현재 테스트들이 여기 속합니다. Spring 컨텍스트의 일부만 띄웁니다.

| 대상 | 방식 |
|---|---|
| 컨트롤러 검증·응답 매핑 | `@WebMvcTest` + `MockMvc` |
| 오류 → HTTP 매핑 | `GlobalExceptionHandler` 포함 |
| 보안 설정 | 현재 `SecurityConfigTest` 방식 |

**우선 보강 대상**: `GlobalExceptionHandler`의 6개 매핑.
현재 `NoSuchElementException`이 500이 되는 문제를 404로 고칠 때
회귀 테스트가 함께 있어야 합니다.

### 3층 — DB 통합 테스트 `제안`

**여기가 가장 큰 공백입니다.**

PostgreSQL 전용 기능에 의존하므로 H2로는 검증할 수 없습니다.

| 대상 | 왜 H2로 안 되는가 |
|---|---|
| 조회수 중복 방지 | `INSERT ... ON CONFLICT DO NOTHING` |
| 닉네임 부분 UNIQUE | `CREATE UNIQUE INDEX ... WHERE` |
| `post_author_ck` CHECK | 문법 차이 |
| 마이그레이션 자체 | PostgreSQL 문법 |

**Testcontainers를 권합니다.** `제안`

```gradle
testImplementation 'org.springframework.boot:spring-boot-testcontainers'
testImplementation 'org.testcontainers:postgresql'
```

`@ServiceConnection`을 쓰면 설정이 거의 필요 없습니다.

이것으로 열리는 것:

| 검증 | 지금은 불가능 |
|---|---|
| 같은 날 두 번 조회해도 조회수 1 | 예 |
| 날짜가 바뀌면 다시 1 증가 | 예 |
| 좋아요 중복 불가 | 예 |
| `like_count`가 음수가 되지 않음 | 예 |
| 마이그레이션 V1~V4가 빈 DB에서 성공 | 예 |
| 중복 아이디·이메일 가입이 409 | 예 |

마지막에서 두 번째가 특히 가치 있습니다 —
현재 마이그레이션이 기동 시점에야 검증됩니다.

### 4층 — 컨테이너 스모크 테스트 `제안`

`plan.md`의 검증 기준을 자동화합니다.

```
docker compose up -d --build
curl -f http://localhost:8080/
curl -f http://localhost:8080/favicon.svg
curl -f http://localhost:8080/api/boards
```

**`Dockerfile`의 COPY 목록 누락을 잡아냅니다.**
이 프로젝트에서 실제로 두 번 문제가 됐던 지점입니다
(Tailwind 4 설정 파일, `public/` 디렉터리).

### 5층 — 프론트엔드 `제안`

현재 테스트 러너 자체가 없습니다. Vite를 쓰므로 Vitest가 자연스럽습니다.

우선순위:

| 대상 | 이유 |
|---|---|
| 회원가입 중복 확인 표시 | `5cca82f`에서 고친 버그. **회귀 위험 있음** |
| `authStore` 파생 상태 | `isAuthenticated`, `isAdmin` 일관성 |
| axios 401 인터셉터 | 세션 만료 처리 |

첫 번째가 중요합니다 — 닉네임 결과 표시가 빠져 있던 버그가
백엔드 테스트로는 잡히지 않았습니다. 백엔드는 정상이었고 화면만 빠져 있었습니다.

## 우선순위 `제안`

위험 대비 비용 순입니다.

| 순위 | 작업 | 층 | 비용 | 잡아내는 것 |
|---|---|---|---|---|
| 1 | `Post.checkEditable` 9경우 | 1 | 매우 낮음 | 권한 우회 |
| 2 | `Board.checkReadable/Writable` | 1 | 매우 낮음 | `V4` 정책 회귀 |
| 3 | CI 워크플로 (기존 테스트 실행) | — | 낮음 | 회귀 전반 |
| 4 | `GlobalExceptionHandler` 매핑 | 2 | 낮음 | 오류 응답 회귀 |
| 5 | Testcontainers + 조회수 | 3 | 중간 | 결정 D2 |
| 6 | 마이그레이션 검증 | 3 | 낮음 | 스키마 오류 |
| 7 | 컨테이너 스모크 | 4 | 중간 | COPY 누락 |
| 8 | Vitest + 가입 화면 | 5 | 중간 | UI 회귀 |
| 9 | 토큰 만료·재사용 | 1+3 | 중간 | 계정 탈취 |

**1과 2를 먼저 하는 이유**: 외부 의존이 전혀 없어 지금 당장 쓸 수 있고,
가장 위험한 영역(권한)을 덮습니다.

**3이 세 번째인 이유**: 테스트를 아무리 써도 실행되지 않으면 의미가 없습니다.
현재 테스트 17개조차 자동으로 돌지 않습니다.

## 테스트 작성 규칙 `제안`

기존 테스트의 관행을 따릅니다.

| 규칙 | 근거 |
|---|---|
| `@DisplayName`을 한국어로, **왜 중요한지까지** | 기존 테스트가 그렇게 되어 있음 |
| 버그를 고칠 때 회귀 테스트를 함께 작성 | 현재 테스트 3종이 모두 이 경로 |
| 테스트는 `.env`를 읽지 않음 | `build.gradle`이 의도적으로 제외 |
| 외부 서비스는 포트를 통해 대역으로 교체 | `RecordingMailSender` 패턴 |

기존 `@DisplayName` 예:

> "공백뿐인 닉네임은 닉네임 없음(null)으로 저장된다 - UNIQUE 인덱스가 NULL은 중복으로 보지 않는다"

무엇을 검증하는지와 **왜 그래야 하는지**가 함께 있습니다. 이 형식을 유지하세요.

### `.env`를 읽지 않는 이유 `확인됨`

```gradle
tasks.named('bootRun') {
    // .env 를 넘김
}
// test 에는 넘기지 않음
```

주석에 명시되어 있습니다 —
"테스트가 개발자 로컬의 .env 값에 따라 달라지면 안 되기 때문이다".

Testcontainers를 도입해도 이 원칙을 지켜야 합니다.

## 커버리지 `제안`

**지금은 커버리지 목표를 세우지 않는 것을 권합니다.**

테스트가 17개뿐인 상태에서 수치 목표를 세우면
통과할 수 없거나, 의미 없는 테스트를 양산하게 됩니다.

먼저 위 우선순위 1~4를 채우고, 그 다음 기준선을 측정해
"떨어지지 않게 유지"하는 방식이 낫습니다.

## 관련 문서

- [../features/acceptance-criteria.md](../features/acceptance-criteria.md) — 검증 기준 목록
- [infrastructure/ci-cd.md](infrastructure/ci-cd.md) — 실행 자동화
- [../security/security-test-plan.md](../security/security-test-plan.md) — 보안 테스트
- [quality-attributes.md](quality-attributes.md) — 테스트 용이성
