# 데이터 등급 분류

> 상태: 저장 데이터 목록은 `확인됨`(스키마 기준), 등급 체계는 `제안`입니다.

## 등급 정의 `제안`

| 등급 | 정의 | 유출 시 영향 |
|---|---|---|
| **C3 — 기밀** | 유출 시 계정 탈취·시스템 침해로 직결 | 심각 |
| **C2 — 민감** | 개인 식별 정보. 유출 시 프라이버시 침해 | 높음 |
| **C1 — 내부** | 운영 정보. 공개되면 공격에 도움 | 중간 |
| **C0 — 공개** | 서비스상 이미 공개됨 | 없음 |

## C3 — 기밀 `확인됨`

| 데이터 | 위치 | 보호 |
|---|---|---|
| 회원 비밀번호 | `member.password_hash` | bcrypt |
| 비회원 글 비밀번호 | `post.guest_password_hash` | bcrypt |
| 인증·재설정 토큰 | `verification_token.token_hash` | SHA-256, 원문 미저장 |
| 세션 ID | 쿠키 `JSESSIONID` | `HttpOnly`. **`Secure` 없음** |
| SMTP 비밀번호 | `.env` | 평문 파일 |
| CAPTCHA 시크릿 | `.env` | 평문 파일 |
| DB 비밀번호 | **`application.yml`, `docker-compose.yml`** | **평문, 커밋됨** |

### 보호 수준 평가 `확인됨`

| 항목 | 평가 |
|---|---|
| 비밀번호 해시 | **양호** — bcrypt, 알고리즘 접두사로 교체 가능 |
| 토큰 해시 | **양호** — 원문이 DB에 없음 |
| 세션 쿠키 | **취약** — `Secure` 없어 평문 전송 가능 (T-001) |
| DB 비밀번호 | **취약** — 저장소에 평문 커밋 |

### 토큰 원문의 취급 `확인됨`

원문은 세 곳을 지납니다.

```
UUID.randomUUID()  →  이벤트 객체  →  메일 본문(링크)  →  사용자 메일함
                                          │
                                          └─ SMTP 미설정 시 로그에도 남음
```

DB에는 해시만 들어갑니다. 다만 `LoggingMailSender` 폴백 상태에서는
**본문 전체가 로그에 남아 토큰 원문이 노출**됩니다.
개발 환경에서는 의도된 동작이지만 운영에서는 사고입니다.

## C2 — 민감 (개인정보) `확인됨`

| 데이터 | 위치 | 필수 | 사용처 |
|---|---|---|---|
| 이메일 | `member.email` | 예 | 인증, 복구 |
| 실명 | `member.name` | 예 | 표시·확인 |
| 휴대전화 | `member.phone` | **예** | **없음** |
| 로그인 아이디 | `member.username` | 예 | 인증 + **화면 노출** |

### 저장 형태 `확인됨`

| 항목 | 상태 |
|---|---|
| 암호화 | **없음** — 전부 평문 |
| 마스킹 | 없음 |
| 접근 제한 | DB 계정 수준만 |

이메일·전화번호가 평문으로 저장됩니다.

### `phone`이 특히 문제입니다 `미결정`

**필수로 수집하는데 어디서도 읽지 않습니다.**

- DB: `NOT NULL`
- API: `@NotBlank`
- `UI.md`: **선택 항목**으로 그림
- 사용처: 없음

수집하지만 쓰지 않는 필수 항목은 최소 수집 원칙과 충돌합니다.
유출 시 피해만 있고 얻는 것이 없습니다.

### `username`이 C2인 이유 `확인됨`

로그인 아이디는 인증 요소의 절반입니다.
그런데 **모든 방문자에게 공개**됩니다.

```java
.authorName(p.getMember() != null ? p.getMember().getUsername() : p.getGuestNickname())
```

(`post/adapter/in/web/dto/PostResponse.java:38`,
`PostListItemResponse.java:19`, `CommentResponse.java:23`)

자유게시판은 비회원도 볼 수 있어 **아이디 수집이 가능**합니다.
로그인 시도 제한이 없는 점(T-008)과 결합하면 공격 대상 목록이 됩니다.

`V3`에서 `nickname` 컬럼을 추가한 목적이 표시명인데 쓰이지 않고 있습니다.

**대응** `제안`: `authorName`을 `nickname ?? name`으로 변경.

## C1 — 내부 `확인됨`

| 데이터 | 위치 | 노출 시 |
|---|---|---|
| 계정 상태 | `member.status` | `GET /api/me`로 본인만 |
| 역할 | `member.role` | 동일 |
| 조회 기록 | `post_view_log` | API 미노출 |
| 좋아요 기록 | `post_like` | 집계만 노출 |
| 첨부 저장명 | `attachment.stored_name` | **API로 노출** |
| 삭제된 글·댓글 | `deleted_at` 설정된 행 | API 미노출 |
| SQL 로그 | stdout | `show-sql: true` |

### `stored_name`이 사실상 접근 토큰 `확인됨`

`GET /api/files/{storedName}`에 인증이 없으므로
UUID를 아는 것이 곧 접근 권한입니다(capability URL).

`PostResponse.attachments`에 실려 나가는데,
그 응답 자체는 게시판 권한 검사를 거칩니다.
따라서 **정상 경로로는 권한 있는 사람만 UUID를 얻습니다.**

문제는 유출 후입니다 — Referer, 히스토리, 공유 링크로 새어나가면
**취소할 방법이 없습니다.**

### 삭제된 데이터 `확인됨`

소프트 삭제라 행이 남습니다.
API로는 노출되지 않지만 **DB 접근 권한이 있으면 전부 보입니다.**

사용자는 삭제했다고 생각하지만 실제로는 보존됩니다.
→ [privacy.md](privacy.md), [../technology/data/data-retention.md](../technology/data/data-retention.md)

## C0 — 공개 `확인됨`

| 데이터 | 노출 범위 |
|---|---|
| 게시판 목록·정책 | 누구나 (`GET /api/boards`) |
| 자유게시판 글 제목·본문 | 누구나 |
| 비회원 글 닉네임 | 누구나 |
| 조회수·좋아요 수 | 게시판 권한에 따름 |
| 작성 시각 | 동일 |

`GET /api/boards`가 권한 검사 없이 전체 목록을 반환하므로
**어떤 게시판이 회원제인지도 공개 정보**입니다. 의도된 설계입니다.

## 등급별 취급 규칙 `제안`

| 규칙 | C3 | C2 | C1 | C0 |
|---|---|---|---|---|
| 평문 저장 금지 | 필수 | 권장 | — | — |
| 전송 암호화(HTTPS) | 필수 | 필수 | 권장 | — |
| 로그 기록 금지 | 필수 | 필수 | — | — |
| 접근 감사 | 필수 | 권장 | — | — |
| 저장소 커밋 금지 | 필수 | 필수 | — | — |
| 보존 기간 정의 | 권장 | 필수 | 권장 | — |

### 현재 위반 `확인됨`

| 위반 | 등급 | 항목 |
|---|---|---|
| 전송 암호화 없음 | C3, C2 | HTTPS 강제 없음 |
| 저장소 커밋 | C3 | DB 비밀번호 |
| 로그 기록 | C2 | `show-sql: true` |
| 로그 기록 | C3 | `MAIL_DEBUG=true` 시, `LoggingMailSender` 폴백 시 |
| 접근 감사 | C3, C2 | 감사 로그 전무 |
| 보존 기간 | C2 | 미정의 |

**전송 암호화가 없는 것이 가장 큽니다.** C3와 C2가 동시에 노출됩니다.

## 데이터 흐름별 노출 지점 `확인됨`

```
[C3] 비밀번호
   브라우저 → 평문 HTTP → 서버 → bcrypt → DB
              ★ 여기가 취약 (T-001)

[C3] 세션 쿠키
   서버 → Set-Cookie (Secure 없음) → 브라우저 → 평문 HTTP → 서버
                                                 ★ 취약

[C3] 인증 토큰 원문
   서버 → 메일 본문 → SMTP → 사용자 메일함
                 └→ (폴백 시) 로그  ★ 운영에서 사고

[C2] 이메일·이름
   DB (평문) → SQL 로그 (show-sql)  ★ 노출
             → API 응답 (본인만)

[C2] username
   DB → API 응답 authorName → 누구나  ★ 설계 문제
```

## 우선 조치 `제안`

| 순위 | 조치 | 대상 등급 |
|---|---|---|
| 1 | HTTPS + 쿠키 `Secure` | C3, C2 |
| 2 | `authorName`을 닉네임으로 | C2 |
| 3 | `show-sql`을 환경 변수로 | C2 |
| 4 | DB 자격 증명을 `.env`로 | C3 |
| 5 | `phone`을 선택으로 또는 제거 | C2 |
| 6 | 감사 로그 도입 | C3, C2 |
| 7 | 보존 기간 정의·구현 | C2 |

## 관련 문서

- [privacy.md](privacy.md) — 개인정보 처리
- [secrets-management.md](secrets-management.md) — 비밀값
- [../technology/data/data-model.md](../technology/data/data-model.md) — 저장 컬럼
- [../technology/data/data-retention.md](../technology/data/data-retention.md) — 보존
- [../business/compliance-matrix.md](../business/compliance-matrix.md) — 규제 대응
