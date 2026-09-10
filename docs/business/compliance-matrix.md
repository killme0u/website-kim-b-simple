# 규제 대응 현황

> 상태: **골격 문서**. 아래 대응 상태는 코드에서 확인한 기술적 사실이며,
> **법적 준수 여부에 대한 판단이 아닙니다.** 실제 준수 판단은 법무 검토가 필요합니다. `제안`

## 이 문서의 한계

저장소에는 개인정보처리방침, 이용약관, 사업자 정보, 수집·이용 동의 문구가 없습니다.
따라서 이 문서는 **어떤 규제 항목에 대해 기술적 수단이 있는가**만 정리합니다.

확인된 것: `SignupCommand`에 `termsAccepted` 필드가 있으나
(`member/adapter/in/web/dto/SignupCommand.java:37`),
**검증도 저장도 되지 않습니다.** `@NotNull`도 없고 `SignupService`가 읽지도 않습니다.
동의를 받았다는 기록이 어디에도 남지 않습니다. `확인됨`

## 수집하는 개인정보 `확인됨`

`member` 테이블 기준 (`V1__init.sql`, `V3__add_member_nickname.sql`)

| 항목 | 컬럼 | 필수 | 용도 |
|---|---|---|---|
| 아이디 | `username` | 필수 | 로그인 식별자 |
| 비밀번호 | `password_hash` | 필수 | 인증 (bcrypt 해시) |
| 이름 | `name` | 필수 | 표시·본인 확인 |
| 닉네임 | `nickname` | 선택 | 표시명 |
| 이메일 | `email` | 필수 | 인증, 비밀번호 재설정, 아이디 찾기 |
| 휴대전화 | `phone` | **필수** | 용도 불명 `미결정` |

### 휴대전화 문제 `미결정`

- DB: `NOT NULL` (`V1__init.sql`)
- API: `@NotBlank` (`SignupCommand.java:29-31`)
- `UI.md`: **선택 항목**으로 그림
- 실제 사용처: **없음.** 어디서도 읽지 않습니다

수집하지만 쓰지 않는 필수 항목입니다. 개인정보 최소 수집 원칙과 충돌합니다.
`todo.md`에 UI-구현 불일치로 등록되어 있습니다.

## 개인정보 관련 대응 현황

| 항목 | 기술적 수단 | 상태 |
|---|---|---|
| 수집 최소화 | — | 휴대전화 미사용 수집 중 `미결정` |
| 수집·이용 동의 기록 | `termsAccepted` 필드만 존재 | **미구현** |
| 비밀번호 암호화 | bcrypt (`DelegatingPasswordEncoder`) | 구현됨 `확인됨` |
| 전송 구간 암호화 | — | **미구현** — HTTPS 강제 설정 없음 |
| 접근 권한 통제 | 세션 인증 + 도메인 권한 판정 | 구현됨 `확인됨` |
| 접속 기록 보관 | — | **미구현** — 감사 로그 없음 |
| 파기 절차 | `MemberStatus.DELETED` enum만 존재 | **미구현** — 전환 경로 없음 |
| 보유 기간 정의 | — | **미정의** |
| 열람·정정 요구 대응 | 마이페이지에서 조회만 가능 | **부분** |
| 개인정보처리방침 게시 | — | **없음** |
| 암호화 대상 항목 (이메일·전화) | 평문 저장 | **미적용** |

## 전송 구간 암호화 `확인됨`

HTTPS를 강제하는 설정이 없습니다.

- `SecurityConfig`에 `requiresChannel()` 없음
- `server.ssl.*` 설정 없음
- 세션 쿠키에 `Secure` 속성 지정 없음
- `docker-compose.yml`이 8080을 평문으로 노출

리버스 프록시에서 TLS를 종단하는 구성을 전제한 것으로 보이나,
그 구성이 문서에도 코드에도 없습니다.
→ [../security/security-requirements.md](../security/security-requirements.md) SR-002

## 접근성 (웹 접근성) `미결정`

| 항목 | 상태 |
|---|---|
| 시맨틱 마크업 | COSS UI(Base UI 기반) 사용 — 컴포넌트 수준에서는 접근성 고려됨 |
| 키보드 탐색 | 미검증 |
| 대체 텍스트 | 미검증 |
| 색 대비 | 미검증 |
| 자동화 검사 | 없음 |

`@base-ui/react`는 접근성을 설계 목표로 삼는 라이브러리라 기본 컴포넌트는 유리한 출발점입니다.
그러나 실제 화면에 대한 검증 기록이 없습니다.

## 라이선스 `확인됨`

| 항목 | 상태 |
|---|---|
| 프로젝트 라이선스 파일 | **없음** — `LICENSE` 파일 부재 |
| 의존성 라이선스 검토 | 없음 |
| 의존성 목록 | `backend-springboot/build.gradle`, `frontend-react/package.json` |

주요 의존성은 Apache-2.0(Spring), MIT(React 생태계) 계열이나 전수 확인은 되지 않았습니다.

## 우선 조치 제안 `제안`

법적 판단이 필요 없고 명백히 빠진 것부터입니다.

1. **`termsAccepted`를 검증하고 동의 시각을 저장** — 필드가 이미 있으므로 작은 변경입니다
2. **휴대전화를 선택으로 바꾸거나 사용처를 정의** — 안 쓰는 필수 수집은 정당화하기 어렵습니다
3. **HTTPS 강제와 쿠키 `Secure`·`SameSite` 속성** — 운영 배포 전 필수
4. **`DELETED` 전환 경로 구현** — 탈퇴 요구에 응할 수단이 없습니다
5. **개인정보처리방침·이용약관 작성** — 법무 검토 필요

## 관련 문서

- [../security/privacy.md](../security/privacy.md) — 개인정보 처리 상세
- [../security/data-classification.md](../security/data-classification.md) — 데이터 등급
- [../technology/data/data-retention.md](../technology/data/data-retention.md) — 보존·파기
