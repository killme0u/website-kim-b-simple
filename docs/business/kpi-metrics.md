# 성과 지표

> 상태: **대부분 `제안`**. 저장소에 분석 도구, 지표 수집 코드, 대시보드가 없습니다.
> 아래는 골격이며 채택 전까지 구속력이 없습니다.

## 현재 측정 가능한 것 `확인됨`

지표 수집 인프라는 없지만, DB에 이미 쌓이고 있어 SQL 한 줄로 뽑을 수 있는 값들입니다.

| 지표 | 계산 방법 | 근거 테이블 |
|---|---|---|
| 누적 가입자 | `SELECT count(*) FROM member` | `member` |
| 인증 완료율 | `ACTIVE / (PENDING + ACTIVE)` | `member.status` |
| 인증 대기 적체 | `count(*) WHERE status='PENDING'` | `member.status` |
| 게시판별 글 수 | `GROUP BY board_id WHERE deleted_at IS NULL` | `post` |
| 비회원 글 비율 | `count(member_id IS NULL) / count(*)` | `post` |
| 일별 순 조회 | `GROUP BY viewed_on` | `post_view_log` |
| 좋아요 총량 | `count(*) FROM post_like` | `post_like` |
| 댓글 참여율 | 댓글 있는 글 / 전체 글 | `comment`, `post` |
| 첨부 사용량 | `SUM(byte_size)` | `attachment` |

`post_view_log`는 **회원 조회만** 기록합니다. 비회원 조회는 `post.view_count`에만
반영되고 로그가 남지 않으므로, 일별 순 조회는 회원 기준입니다.
(`post/application/PostService.java:50-56`)

## 제안하는 지표 체계 `제안`

### 1. 획득 (Acquisition)

| 지표 | 정의 | 목표 | 수집 |
|---|---|---|---|
| 주간 신규 가입 | `member.created_at` 주 단위 집계 | 미정 | SQL |
| 가입 시도 대비 완료율 | 완료 / 시도 | 미정 | **불가 — 시도를 기록하지 않음** |
| CAPTCHA 차단율 | 실패 / 전체 시도 | 미정 | **불가 — 실패를 기록하지 않음** |

가입 시도와 CAPTCHA 실패는 현재 어디에도 남지 않습니다.
`SignupService.signup`이 CAPTCHA 실패 시 예외를 던지고 끝냅니다
(`member/application/SignupService.java:30-32`). 측정하려면 이벤트 기록이 필요합니다.

### 2. 활성화 (Activation)

| 지표 | 정의 | 목표 | 수집 |
|---|---|---|---|
| 이메일 인증 완료율 | `ACTIVE / 전체 가입` | 미정 | SQL 가능 |
| 가입→인증 소요 시간 | `used_at - (expires_at - 24h)` | 미정 | SQL 가능 (`verification_token`) |
| 인증 링크 만료율 | 만료된 토큰 / 발급 토큰 | 미정 | SQL 가능 |

인증 완료율은 지금 가장 중요한 지표입니다. `todo.md`에 "발송된 메일이 없음"이
검토 항목으로 올라 있어, 이 값이 낮다면 메일 발송 자체를 의심해야 합니다.
→ [../technology/infrastructure/runbooks/RB-001-mail-not-sent.md](../technology/infrastructure/runbooks/RB-001-mail-not-sent.md)

### 3. 참여 (Engagement)

| 지표 | 정의 | 목표 | 수집 |
|---|---|---|---|
| 글당 평균 댓글 수 | `comment / post` | 미정 | SQL 가능 |
| 글당 평균 좋아요 | `post_like / post` | 미정 | SQL 가능 |
| 게시판별 활성도 | 게시판별 주간 신규 글 | 미정 | SQL 가능 |
| 재방문율 | 같은 회원의 서로 다른 `viewed_on` 수 | 미정 | SQL 가능 (`post_view_log`) |

`post_view_log`가 `(post_id, member_id, viewed_on)` 조합으로 쌓이므로,
회원별 활동 일수를 세면 재방문율의 근사치가 나옵니다. 원래 목적은 조회수 중복 방지지만
부수적으로 행동 로그 역할을 합니다.

### 4. 운영 건전성

| 지표 | 정의 | 목표 | 수집 |
|---|---|---|---|
| API 오류율 | 5xx / 전체 | 미정 | **불가 — 메트릭 수집 없음** |
| 응답 시간 p95 | — | 미정 | **불가** |
| 메일 발송 실패율 | 실패 / 시도 | 미정 | **불가 — 로그만 남음** |
| 업로드 저장소 사용량 | 볼륨 사용률 | 미정 | 인프라 측정 |

앞의 셋은 `spring-boot-starter-actuator`와 Micrometer가 없어 측정할 수 없습니다.
(`backend-springboot/build.gradle`에 의존성 없음)
메일 실패는 `SignupMailListener.send`가 `log.error`로만 남깁니다
(`member/application/SignupMailListener.java:78-81`).
→ [../technology/infrastructure/observability.md](../technology/infrastructure/observability.md)

## 측정을 시작하려면 `제안`

우선순위 순입니다.

1. **Actuator + Micrometer 추가** — 오류율·응답시간·JVM 지표가 한 번에 열립니다. 의존성 한 줄.
2. **메일 발송 결과를 DB에 기록** — 현재 로그만 남아 집계가 불가능합니다.
3. **가입 시도·CAPTCHA 실패 이벤트 기록** — 스팸 유입 규모를 알 수 없는 상태입니다.
4. **대시보드** — 위 셋이 갖춰진 뒤에 의미가 있습니다.

목표 수치는 서비스가 실제로 운영되어 기준선(baseline)이 생긴 뒤에 정하는 것이 맞습니다.
지금 숫자를 적으면 근거 없는 값이 문서에 고정됩니다.

## 관련 문서

- [../technology/infrastructure/observability.md](../technology/infrastructure/observability.md) — 수집 인프라 현황
- [roadmap.md](roadmap.md) — 지표 수집 도입 시점
- [../technology/data/data-model.md](../technology/data/data-model.md) — 집계 대상 테이블
