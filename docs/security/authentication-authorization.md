# 인증·인가

> 상태: `확인됨` — 코드에서 도출.
> 기능 관점 명세는 [../features/specifications/FR-001-authentication.md](../features/specifications/FR-001-authentication.md).
> 이 문서는 **보안 관점**에서 같은 주제를 다룹니다.

## 인증 방식 선택 `확인됨`

**세션 쿠키 + CSRF 토큰**입니다. JWT가 아닙니다.

### 세션을 택한 결과

| 항목 | 세션 (현재) | JWT (대안) |
|---|---|---|
| 즉시 무효화 | **가능** (서버가 상태 보유) | 어려움 (만료까지 유효) |
| 동시 세션 제한 | **가능** (`maximumSessions(1)`) | 별도 저장소 필요 |
| 수평 확장 | **불가** (인메모리) | 쉬움 |
| 재시작 시 | **전원 로그아웃** | 영향 없음 |
| XSS 시 토큰 탈취 | `HttpOnly`로 완화 | localStorage면 노출 |
| CSRF | 방어 필요 | 헤더 방식이면 불필요 |

보안 관점에서는 **세션이 유리한 선택**입니다.
즉시 무효화가 가능하고 `HttpOnly` 쿠키라 XSS로 탈취하기 어렵습니다.

대가는 확장성입니다 — 인메모리 세션이라 인스턴스를 늘릴 수 없습니다.

## 세션 보안 설정 `확인됨`

```java
.sessionManagement(session -> session
    .sessionFixation(fixation -> fixation.changeSessionId())
    .sessionConcurrency(concurrency -> concurrency.maximumSessions(1))
);
```

(`config/SecurityConfig.java:61-65`)

| 설정 | 효과 |
|---|---|
| `changeSessionId()` | 로그인 시 세션 ID 재발급 — **세션 고정 공격 방어** |
| `maximumSessions(1)` | 계정당 1개. 새 로그인이 기존 세션을 만료시킴 |

### `maximumSessions(1)`의 보안 효과 `확인됨`

의도는 정책이지만 보안 부수효과가 있습니다.

**계정이 탈취되면 원 사용자가 즉시 쫓겨납니다.**
사용자가 이상을 바로 알아차립니다. 조용한 계정 탈취가 어렵습니다.

반대로 공격자 입장에서도 피해자가 로그인하면 자기 세션이 끊깁니다.

### 미설정 항목 `미결정`

| 항목 | 상태 |
|---|---|
| 세션 타임아웃 | 서블릿 컨테이너 기본값 (보통 30분) |
| 쿠키 `Secure` | **없음** |
| 쿠키 `SameSite` | **없음** |
| 쿠키 이름 변경 | 기본 `JSESSIONID` (기술 스택 노출) |

`plan.md` 선결 결정 #4(로그인 유지 정책)가 여기에 걸려 있습니다.

## 계정 상태 기반 차단 `확인됨`

Spring Security 표준 훅에 매핑되어 있습니다.

```java
@Override
public boolean isAccountNonLocked() {
    return member.getStatus() != MemberStatus.SUSPENDED;
}

@Override
public boolean isEnabled() {
    return member.getStatus() == MemberStatus.ACTIVE || member.getStatus() == MemberStatus.PENDING;
}
```

(`member/application/CustomUserDetails.java:46-59`)

| 상태 | 로그인 |
|---|---|
| `PENDING` | **가능** |
| `ACTIVE` | 가능 |
| `SUSPENDED` | 차단 |
| `DELETED` | 차단 |

### `PENDING` 허용의 보안적 의미 `미결정`

명시적 설계입니다(`isEnabled()`가 두 상태를 나열).

**결과: 이메일 인증이 접근을 통제하지 않습니다.**

`Board.checkReadable`이 `actor.isEmpty()`만 보고 `status`를 무시하므로
(`board/domain/Board.java:44`), `PENDING` 계정도 회원제 게시판에 들어갑니다.

| 관점 | 평가 |
|---|---|
| 보안 | 이메일 소유 확인이 실질적 관문이 아님. 아무 주소로나 가입 가능 |
| 가용성 | 메일이 안 가도 사용자가 완전히 막히지 않음 (현재 메일 문제 고려 시 이점) |

`todo.md`의 메일 발송 문제가 미해결인 상태에서 이것을 조이면
신규 사용자가 로그인조차 못 하게 됩니다. **순서가 중요합니다.**

### `SUSPENDED`·`DELETED`는 도달 불가 `미결정`

두 상태로 **전환하는 코드가 없습니다.**
판정 로직은 동작하지만 실제로는 도달하지 않습니다.

`DELETED`로 바꿀 수 없다는 것은 **탈퇴 요구에 응할 수단이 없다**는 뜻입니다.
→ [privacy.md](privacy.md)

## 로그인 방어의 공백 `확인됨`

`POST /api/auth/login`에 대한 방어입니다.

| 방어 | 상태 |
|---|---|
| CSRF | **면제** (구조적으로 불가피) |
| CAPTCHA | 없음 |
| 시도 횟수 제한 | 없음 |
| 계정 잠금 | 없음 |
| 속도 제한 | 없음 |
| 실패 로그 | **없음** |

**무차별 대입에 완전히 열려 있습니다.**

### 위험을 키우는 요소 `확인됨`

1. `GET /api/members/username-availability`가 유효한 아이디를 알려줌
2. 비밀번호 복잡도 요구가 없어 `aaaaaaaa` 같은 값이 존재할 수 있음
3. 실패가 기록되지 않아 **공격 중인지 알 수 없음**

CSRF 면제 자체는 문제가 아닙니다 — 로그인 전에는 세션이 없어
토큰을 받을 수 없으므로 구조적으로 불가피합니다.
문제는 **그 자리를 대신 막을 것이 아무것도 없다**는 점입니다.

가입에는 CAPTCHA가 있는데 로그인에는 없습니다.

### 대응 `제안`

```java
@Component
public class LoginAttemptListener {
    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent e) {
        // IP·계정별 실패 카운트, 임계값 초과 시 차단
        // 동시에 WARN 로그 — 현재는 실패가 어디에도 남지 않음
    }
}
```

로그만 남겨도 큰 개선입니다. 지금은 공격 여부조차 알 수 없습니다.

## 인가 — 2층 구조 `확인됨`

### 층 1: HTTP 경로 규칙

`authenticated()`가 실제로 막는 경로는 소수입니다.

| 막히는 것 | |
|---|---|
| `/api/me`, `/api/me/**` | 마이페이지 |
| `POST /api/posts/*/like` | 좋아요 |
| 댓글 관련 전부 | |

나머지는 `permitAll`이고 판정이 층 2로 갑니다.

### 층 2: 도메인 판정

| 판정자 | 대상 | 실패 시 |
|---|---|---|
| `Board.checkReadable` | 게시판 읽기 | 401 |
| `Board.checkWritable` | 게시판 쓰기 | 401 |
| `Post.checkEditable` | 글 수정·삭제 | 403 |
| `CommentService` 내부 | 댓글 | 401 / 403 |

### 판정 순서가 중요합니다 `확인됨`

`Post.checkEditable`의 분기입니다.

```
로그인?
├─ 예 ── 관리자? → 통과
│        작성자? → 통과
│        아니면  → 403
└─ 아니오 ── 회원 글?  → 403
             비회원 글 → 비밀번호 검증
```

**로그인 상태에서는 비밀번호 경로로 갈 수 없습니다.**

보안상 올바릅니다 — 로그인한 사용자가 비밀번호를 추측해
남의 비회원 글을 고치는 것을 막습니다.

부작용: 비회원으로 글을 쓴 뒤 로그인하면 자기 글을 못 고칩니다. `미결정`

## 역할 `확인됨`

```java
List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()))
```

| 역할 | 권한 |
|---|---|
| `USER` | 기본 |
| `ADMIN` | 모든 글·댓글 수정·삭제 |

`@EnableMethodSecurity`가 켜져 있지만 `@PreAuthorize`를 쓰는 곳이 없습니다.
역할 검사는 도메인에서 `MemberRole` 열거형을 직접 비교합니다.

### 관리자 계정을 만들 수 없습니다 `확인됨`

`Member.pending`이 항상 `USER`로 설정합니다(`member/domain/Member.java:76`).
`role`을 바꾸는 코드가 어디에도 없습니다.

**DB를 직접 수정하는 것 외에 방법이 없습니다.**

보안 관점에서는 권한 상승 경로가 없다는 뜻이라 나쁘지 않지만,
운영 관점에서는 관리 기능을 쓸 수 없습니다. `미결정`

관리자 계정을 만들게 된다면:
- 생성 경로 자체에 강한 인증 필요
- 관리자 행위는 감사 로그 필수 (현재 없음)

## 토큰 기반 인증 (메일 링크) `확인됨`

비밀번호 재설정과 이메일 인증은 **토큰이 곧 인증**입니다.

| 항목 | 값 |
|---|---|
| 생성 | `UUID.randomUUID()` (122비트 랜덤) |
| 저장 | SHA-256 해시만 (`token_hash`) |
| 전달 | 메일 링크 (URL 인코딩) |
| 이메일 인증 유효기간 | 24시간 |
| 재설정 유효기간 | **1시간** |
| 재사용 | 불가 (`used_at`) |
| 용도 교차 사용 | 불가 (`purpose` 검증) |

```java
public boolean isUsable(String expectedPurpose) {
    return expectedPurpose.equals(this.purpose)
            && this.usedAt == null
            && ZonedDateTime.now().isBefore(this.expiresAt);
}
```

**세 조건을 모두 검사합니다.** 잘 설계된 부분입니다.

### 남은 위험 `미결정`

| 위험 | 내용 |
|---|---|
| 새 토큰 발급 시 기존 토큰 무효화 안 함 | 유효 토큰이 여러 개 공존 |
| 재발송 횟수 제한 없음 | 토큰이 무한히 쌓임 |
| 비밀번호 변경 후 세션 유지 | 탈취된 세션이 살아남음 |
| GET으로 이메일 인증 | 링크 프리페치가 토큰 소비 |

**세 번째가 가장 위험합니다.** 계정이 탈취된 사용자가
비밀번호를 바꿔도 공격자 세션이 그대로 유지됩니다.

`maximumSessions(1)` 덕분에 피해자가 다시 로그인하면
공격자 세션이 끊기긴 하지만, 명시적 무효화가 아니라 우연한 효과입니다.

대응 `제안`: `changePassword` 시 해당 회원의 모든 세션을 만료
(`SessionRegistry` 사용).

## 클라이언트 측 상태 `확인됨`

```ts
api.interceptors.response.use(
  response => response,
  (error) => {
    if (isUnauthorized(error)) {
      useAuthStore.getState().logout();
    }
    return Promise.reject(error);
  },
);
```

(`frontend-react/src/lib/axios.ts:26-34`)

| 성질 | 보안적 의미 |
|---|---|
| 서버 401이 클라이언트 상태를 결정 | **클라이언트가 인가를 판단하지 않음** |
| 라우트 가드 없음 | 우회할 가드가 없음 |
| `persist` 미사용 | 새로고침 시 서버에서 다시 확인 |

클라이언트 상태를 조작해도 서버 판정이 바뀌지 않습니다.
**올바른 구조입니다.**

## 개선 우선순위 `제안`

| 순위 | 항목 | 이유 |
|---|---|---|
| 1 | 쿠키 `Secure`·`SameSite` + HTTPS | 세션 탈취 방어의 전제 |
| 2 | 로그인 실패 로그 | 지금은 공격 여부조차 모름 |
| 3 | 로그인 시도 제한 | 무차별 대입 방어 |
| 4 | 비밀번호 변경 시 세션 무효화 | 탈취 복구 |
| 5 | 세션 타임아웃 명시 | 방치된 세션 |
| 6 | 새 토큰 발급 시 기존 무효화 | 토큰 공존 |
| 7 | 관리자 계정 생성 경로 + 감사 로그 | 운영 필요 시 |

2번이 비용 대비 효과가 가장 큽니다 — 몇 줄로 가시성이 생깁니다.

## 관련 문서

- [../features/specifications/FR-001-authentication.md](../features/specifications/FR-001-authentication.md) — 기능 명세
- [access-control.md](access-control.md) — 권한 매트릭스
- [threat-model.md](threat-model.md) — T-001, T-008
- [../features/state-machines.md](../features/state-machines.md) — 계정 상태
