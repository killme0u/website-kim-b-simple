# 관측 가능성

> 상태: **현황 `확인됨` — 관측 수단이 거의 없습니다.** 제안은 `제안`입니다.

## 현황 `확인됨`

| 항목 | 상태 | 근거 |
|---|---|---|
| Spring Boot Actuator | **없음** | `build.gradle`에 의존성 없음 |
| Micrometer / 메트릭 | **없음** | 동일 |
| 헬스체크 엔드포인트 | **없음** | 동일 |
| 분산 추적 | 없음 | 단일 프로세스라 필요성 낮음 |
| 구조화 로그 (JSON) | 없음 | Spring 기본 패턴 |
| 로그 수집·집계 | 없음 | stdout만 |
| APM | 없음 | |
| 알림 | 없음 | |

**측정할 수 있는 것이 로그뿐입니다.**

## 현재 남는 로그 `확인됨`

| 대상 | 레벨 | 위치 |
|---|---|---|
| SMTP 미설정 폴백 | `WARN` | `config/MailConfig.java:40-43` |
| SMTP 사용 시작 | `INFO` | `MailConfig.java:46` |
| 메일 발송 실패 | `ERROR` | `member/application/SignupMailListener.java:80` |
| 모든 SQL | (Hibernate) | `show-sql: true` |
| SMTP 대화 전체 | (JavaMail) | `MAIL_DEBUG=true`일 때만 |

### 로그가 남지 않는 중요한 사건 `확인됨`

| 사건 | 현재 |
|---|---|
| CAPTCHA 검증 실패 | **로그 없음** — 조용히 `false` 반환 |
| CAPTCHA provider 오류·타임아웃 | **로그 없음** — 예외를 삼킴 |
| 로그인 실패 | 로그 없음 |
| 권한 거부 (403) | 로그 없음 |
| 파일 경로 탈출 시도 | 500 스택트레이스로만 |
| 미처리 예외 (`NoSuchElementException`) | 500 스택트레이스 |

CAPTCHA 실패가 기록되지 않는 것이 특히 문제입니다.

```java
} catch (IOException | RuntimeException e) {
    return false;
}
```

(`captcha/adapter/out/ConfiguredCaptchaVerifier.java:64-66`)

네트워크 오류인지, 잘못된 시크릿인지, 실제 봇인지 **구분할 방법이 없습니다.**
가입이 안 된다는 신고를 받아도 원인을 찾을 수 없습니다.

## `show-sql: true`의 문제 `확인됨`

```yaml
jpa:
  show-sql: true
  properties:
    hibernate:
      format_sql: true
```

환경 변수로 뺄 수 없게 하드코딩되어 있습니다.

| 영향 | 내용 |
|---|---|
| 로그 폭증 | 요청 하나당 SQL 여러 줄 |
| 개인정보 노출 | 파라미터에 이메일·이름이 포함될 수 있음 |
| 성능 | 포맷팅 비용 |
| 신호 대 잡음 | 실제 오류가 SQL에 묻힘 |

`${SHOW_SQL:false}`로 바꾸는 것을 권합니다. `제안`

## 최소 도입 제안 `제안`

### 1단계 — Actuator (가장 큰 효과, 가장 낮은 비용)

```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

의존성 한 줄로 열리는 것:

| 엔드포인트 | 용도 |
|---|---|
| `/actuator/health` | 헬스체크. DB 연결 상태 포함 |
| `/actuator/info` | 빌드 정보 |
| `/actuator/metrics` | JVM·HTTP·DataSource 메트릭 |
| `/actuator/flyway` | 적용된 마이그레이션 목록 |

**보안 주의**: `SecurityConfig`의 `.anyRequest().permitAll()` 때문에
`/actuator/**`가 그대로 공개됩니다. 반드시 함께 막아야 합니다.

```java
.requestMatchers("/actuator/health").permitAll()
.requestMatchers("/actuator/**").hasRole("ADMIN")
```

`ADMIN` 계정을 만드는 경로가 없으므로(현재 미구현),
당장은 `/actuator/health`만 열고 나머지는 `denyAll()`이 안전합니다.

### 헬스체크를 compose에 연결 `제안`

```yaml
app:
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
    interval: 30s
    timeout: 5s
    retries: 3

postgres:
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U board_user -d board_db"]
    interval: 10s
    timeout: 5s
    retries: 5

# app 이 postgres 준비를 기다리게
app:
  depends_on:
    postgres:
      condition: service_healthy
```

현재 `depends_on`은 시작 순서만 보장하고 준비 상태는 보지 않습니다.
postgres가 늦게 뜨면 Flyway가 접속에 실패해 app 기동이 실패합니다.

### 2단계 — 놓치고 있는 로그 채우기 `제안`

우선순위 순입니다.

| 대상 | 레벨 | 남길 내용 |
|---|---|---|
| CAPTCHA 실패 | `WARN` | 모드, 실패 사유(네트워크/거부), remoteAddr |
| CAPTCHA provider 오류 | `ERROR` | 예외 메시지 |
| 로그인 실패 | `WARN` | username, remoteAddr |
| 경로 탈출 시도 | `WARN` | 요청된 `storedName`, remoteAddr |
| 미처리 예외 | `ERROR` | 이미 남지만 404를 분리하면 줄어듦 |

**비밀번호·토큰 원문은 절대 남기지 않습니다.**

### 3단계 — 구조화 로그 `제안`

JSON 로그로 바꾸면 수집·검색이 쉬워집니다.
`logstash-logback-encoder` 등을 쓰되, 로컬 개발에서는 사람이 읽는 형식을 유지하는 것이 좋습니다.

### 4단계 — 지표 수집 `제안`

[../../business/kpi-metrics.md](../../business/kpi-metrics.md)에 정리한 지표 중
현재 측정 불가능한 것들이 여기서 열립니다.

| 지표 | 필요한 것 |
|---|---|
| API 오류율, p95 응답시간 | Actuator + Micrometer |
| 메일 발송 성공률 | **발송 결과를 DB에 기록** |
| CAPTCHA 차단율 | 실패 이벤트 기록 |
| 가입 전환율 | 가입 시도 기록 |

메일 발송 결과는 메트릭만으로 부족합니다 —
"누구에게 언제 보냈고 성공했는가"를 알아야 사용자 문의에 답할 수 있습니다.
테이블 하나가 필요합니다.

## 알림 `제안`

관측이 갖춰진 뒤에 의미가 있습니다. 우선순위 순:

| 조건 | 심각도 |
|---|---|
| 앱이 응답하지 않음 | 긴급 |
| DB 연결 실패 | 긴급 |
| 메일 발송 실패율 급증 | 높음 |
| 5xx 급증 | 높음 |
| 디스크 사용률 80% 초과 | 높음 (업로드가 무인증이라 특히) |
| CAPTCHA 실패율 급증 | 중간 (봇 유입 신호) |

## 지금 당장 할 수 있는 진단 `확인됨`

관측 인프라 없이도 확인 가능한 것들입니다.

```bash
# 어느 메일 발송기를 쓰는지
docker compose logs app | grep -iE "SMTP (메일 발송을 사용|설정이 비어)"

# 메일 발송 실패
docker compose logs app | grep "메일 발송에 실패"

# 로그 전용 발송기가 남긴 메일 본문 (인증 링크 확인용)
docker compose logs app | grep -A 20 "verify-email"

# 적용된 마이그레이션
docker exec board-postgres psql -U board_user -d board_db \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"

# 계정 상태 분포
docker exec board-postgres psql -U board_user -d board_db \
  -c "SELECT status, count(*) FROM member GROUP BY status;"

# 인증 대기 적체
docker exec board-postgres psql -U board_user -d board_db \
  -c "SELECT count(*) FROM member WHERE status = 'PENDING';"
```

마지막 두 개가 `todo.md`의 메일 문제를 진단하는 출발점입니다.

## 도입 순서 요약 `제안`

| 순위 | 작업 | 비용 |
|---|---|---|
| 1 | Actuator 추가 + `/actuator/**` 접근 제한 | 매우 낮음 |
| 2 | compose 헬스체크 + `condition: service_healthy` | 매우 낮음 |
| 3 | `show-sql`을 환경 변수로 | 매우 낮음 |
| 4 | CAPTCHA·로그인 실패 로그 추가 | 낮음 |
| 5 | 메일 발송 결과 테이블 | 중간 |
| 6 | 구조화 로그 + 수집 | 중간 |
| 7 | 알림 | 높음 |

1~3은 각각 몇 줄이면 끝나고, 운영 시작 전에 반드시 있어야 합니다.

## 관련 문서

- [../../business/kpi-metrics.md](../../business/kpi-metrics.md) — 측정하려는 지표
- [runbooks/README.md](runbooks/README.md) — 장애 대응
- [environments.md](environments.md) — 설정
- [../../security/incident-response.md](../../security/incident-response.md)
