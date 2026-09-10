# 비밀값 관리

> 상태: `확인됨` — `.env.example`, `application.yml`, `docker-compose.yml`, `Dockerfile` 기준.

## 원칙 `확인됨`

**비밀값은 저장소 밖에서 주입한다.** `.env`는 `.gitignore` 대상이고,
커밋되는 것은 양식인 `.env.example`뿐입니다.

`.env.example` 상단에 명시되어 있습니다 —
"이 파일은 커밋되는 양식이다. 실제 값을 여기에 적지 말 것."

## 주입 경로 `확인됨`

```
application.yml 기본값
      ▲ 덮어씀
저장소 루트 .env       (spring.config.import, optional)
      ▲ 덮어씀
OS 환경 변수 / 컨테이너 env
```

`bootRun`은 추가로 절대 경로를 넘겨 작업 디렉터리와 무관하게 같은 파일을 봅니다
(`backend-springboot/build.gradle`).

**테스트에는 넘기지 않습니다** — 테스트가 개발자 로컬 값에 흔들리지 않게.

## 비밀값 목록 `확인됨`

### 저장소 밖에서 주입되는 것 (올바름)

| 값 | 변수 | 민감도 |
|---|---|---|
| SMTP 계정 | `MAIL_USERNAME` | 중간 |
| SMTP 비밀번호 | `MAIL_PASSWORD` | **높음** |
| CAPTCHA 시크릿 | `CAPTCHA_SECRET` | **높음** |
| DB 호스트 | `PGSQL_HOST` | 낮음 (환경 정보) |

### 저장소에 평문으로 커밋된 것 (위반) `확인됨`

| 값 | 위치 | 민감도 |
|---|---|---|
| DB 계정 | `application.yml` `username: board_user` | 중간 |
| DB 비밀번호 | `application.yml` `password: board_password` | **높음** |
| DB 비밀번호 | `docker-compose.yml` `POSTGRES_PASSWORD=board_password` | **높음** |
| DB 계정 | `docker-compose.yml` `POSTGRES_USER=board_user` | 중간 |

**SMTP와 CAPTCHA는 `.env`로 뺐는데 DB만 남았습니다.**

로컬 개발 편의를 위한 선택으로 보이지만, 같은 값이 운영으로 이어지면
기본 자격 증명이 공개된 상태가 됩니다.
`docker-compose.yml`은 5432를 호스트에 노출하기까지 합니다.

### 대응 `제안`

```yaml
# application.yml
datasource:
  url: jdbc:postgresql://${PGSQL_HOST:localhost}:${PGSQL_PORT:5432}/${PGSQL_DB:board_db}
  username: ${PGSQL_USER:board_user}
  password: ${PGSQL_PASSWORD:board_password}
```

```yaml
# docker-compose.yml
environment:
  - POSTGRES_USER=${PGSQL_USER:-board_user}
  - POSTGRES_PASSWORD=${PGSQL_PASSWORD:-board_password}
```

기본값을 남겨 로컬 편의를 유지하면서 운영에서 덮어쓸 수 있게 합니다.

## `.env` 작성 규칙 `확인됨`

**dotenv가 아니라 `.properties` 문법**입니다. `.env.example`의 경고를 옮깁니다.

| 규칙 | 이유 |
|---|---|
| 값을 따옴표로 감싸지 말 것 | `MAIL_PASSWORD="abcd"` → 따옴표까지 값 |
| `\`는 이스케이프 문자 | 경로는 `/` 또는 `\\` |
| **값에 한글 금지** | ISO-8859-1로 읽혀 깨짐. 비밀번호는 ASCII로 |
| `#`으로 시작하면 주석 | |
| `=` 앞뒤 공백은 값에 미포함 | |
| 기본값을 쓰려면 **줄을 통째로 삭제** | 값만 비우면 빈 문자열이 됨 |

마지막이 가장 자주 사고를 냅니다. `MAIL_SMTP_HOST=`로 두면
빈 문자열이 되어 로그 전용 발송기로 폴백합니다.

## 저장되는 비밀값 `확인됨`

### 해시로만 저장

| 값 | 형태 | 위치 |
|---|---|---|
| 회원 비밀번호 | bcrypt (`{bcrypt}` 접두사) | `member.password_hash` |
| 비회원 글 비밀번호 | bcrypt | `post.guest_password_hash` |
| 인증·재설정 토큰 | SHA-256 hex | `verification_token.token_hash` |

**토큰 원문은 메일 링크에만 존재합니다.**
DB가 유출돼도 인증 링크를 복원할 수 없습니다.

```java
String rawToken = UUID.randomUUID().toString();
VerificationToken token = VerificationToken.emailVerify(
        member, TokenHasher.sha256(rawToken), Duration.ofHours(24));
tokenRepository.save(token);
events.publishEvent(new SignupCompleted(member.getEmail(), rawToken));
```

(`member/application/SignupService.java:40-45`)

`rawToken`은 이벤트로만 전달되고 저장되지 않습니다.

### 저장하지 않는 것

| 값 | 처리 |
|---|---|
| CAPTCHA 토큰 | 검증 후 폐기. DB·로그에 남기지 않음 |

`plan.md`의 규칙 — "토큰 원문을 전역 상태나 저장소에 보관하지 않는다".

## 로그에 남는 비밀값 `확인됨`

| 조건 | 노출 |
|---|---|
| `MAIL_DEBUG=true` | **SMTP 대화 전체 — 인증 정보 포함** |
| `show-sql: true` (**항상 켜짐**) | SQL 파라미터 (이메일·이름 등) |
| `LoggingMailSender` 사용 시 | **메일 본문 = 인증 토큰 원문** |

### `MAIL_DEBUG` `확인됨`

기본값이 `false`이고 `application.yml`에 경고가 있습니다 —
"SMTP 대화 전체(인증 정보 포함)를 stdout에 찍는다. 디버깅할 때만 켠다."

일시적으로 켰다가 **끄는 것을 잊지 않는 것**이 중요합니다.

### `LoggingMailSender`의 부작용 `확인됨`

SMTP 미설정 시 폴백이 메일 본문을 로그로 남깁니다.
본문에는 **인증 토큰 원문이 포함된 링크**가 들어 있습니다.

개발 환경에서는 의도된 동작입니다(로그에서 링크를 꺼내 인증).
**운영에서 이 상태가 되면 로그를 볼 수 있는 사람이 누구의 계정이든 인증·재설정할 수 있습니다.**

기동 로그로 상태를 확인할 수 있습니다.

```bash
docker compose logs app | grep -i "SMTP 설정이 비어 있어"
```

이 로그가 운영에서 보이면 **즉시 조치**해야 합니다.

### `show-sql` `확인됨`

환경 변수로 뺄 수 없게 하드코딩되어 있습니다.
운영에서 개인정보가 로그에 남습니다.

대응 `제안`: `show-sql: ${SHOW_SQL:false}`

## 이미지·저장소 격리 `확인됨`

| 항목 | 상태 |
|---|---|
| `Dockerfile`이 `.env`를 COPY | **하지 않음** (올바름) |
| `.dockerignore` | 존재 |
| compose가 `.env`를 읽어 env로 전달 | 예 |
| `.gitignore`에 `.env` | 예 |

`docker-compose.yml`에 주석으로 명시되어 있습니다 —
"이미지 안에는 `.env`를 넣지 않는다(Dockerfile이 복사하지 않는다)".

### `.dockerignore` 주의 `확인됨`

파일 상단에 경고가 있습니다.

> 여기에 넣은 경로는 COPY 할 수 없게 된다.
> Dockerfile 이 COPY 하는 경로는 절대 넣지 말 것.

`.env`를 여기에 넣는 것은 안전하지만, 빌드에 필요한 경로를 넣으면
빌드가 조용히 깨집니다.

## 검증 `확인됨`

```bash
# .env 가 무시 대상인지
git check-ignore -v .env

# 저장소에 커밋된 적이 있는지
git log --all --full-history -- .env

# 커밋된 파일에 비밀값 패턴이 있는지
git grep -nE "(password|secret|token)\s*[:=]" -- ':!*.md' ':!.env.example'
```

마지막 명령은 현재 `application.yml`과 `docker-compose.yml`의
DB 비밀번호를 찾아냅니다.

## 비밀값 유출 시 대응 `제안`

| 유출 대상 | 조치 |
|---|---|
| SMTP 비밀번호 | 제공자에서 앱 비밀번호 재발급, `.env` 갱신, 재기동 |
| CAPTCHA 시크릿 | provider에서 재발급 |
| DB 비밀번호 | DB 계정 비밀번호 변경, 모든 인스턴스 갱신 |
| `.env`가 커밋됨 | **위 전부 재발급** + git 이력 정리 |

**git 이력에서 파일을 지워도 이미 push된 값은 유출된 것으로 간주**하고
반드시 재발급해야 합니다.

## 없는 것 `미결정`

| 항목 | 영향 |
|---|---|
| 시크릿 관리 도구 (Vault, AWS Secrets Manager 등) | `.env` 평문 파일에만 의존 |
| 비밀값 자동 스캔 (gitleaks 등) | 실수로 커밋해도 탐지 안 됨 |
| 정기 로테이션 | 유출 시 노출 기간이 무한 |
| 접근 감사 | 누가 `.env`를 봤는지 모름 |
| 암호화된 설정 | `.env`가 평문 파일 |

## 우선 조치 `제안`

| 순위 | 작업 | 비용 |
|---|---|---|
| 1 | DB 자격 증명을 `.env`로 분리 | 낮음 |
| 2 | `show-sql`을 환경 변수로 | 매우 낮음 |
| 3 | secret scanning 도입 (GitHub 기본 기능) | 매우 낮음 |
| 4 | 기동 시 `LoggingMailSender` 폴백 경고 강화 | 낮음 |
| 5 | 시크릿 관리 도구 | 높음 (운영 규모에 따라) |

1~3은 각각 몇 분이면 됩니다.

## 관련 문서

- [../technology/infrastructure/environments.md](../technology/infrastructure/environments.md) — 환경 변수 전체
- [../technology/api/integration-contracts.md](../technology/api/integration-contracts.md) — 외부 연동 설정
- [data-classification.md](data-classification.md) — 데이터 등급
- [incident-response.md](incident-response.md) — 유출 사고 대응
