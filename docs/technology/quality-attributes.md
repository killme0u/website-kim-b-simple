# 품질 속성

> 상태: 현재 수준은 `확인됨`(코드·설정 근거), 목표치는 `제안`입니다.
> **측정 수단이 없어 대부분의 수치는 정할 수 없습니다.**

## 요약

| 속성 | 현재 | 주요 제약 |
|---|---|---|
| 보안 | 부분 | HTTPS·무차별대입 방어·업로드 인증 없음 |
| 신뢰성 | 낮음 | 백업·헬스체크·재시도 없음 |
| 가용성 | 낮음 | 단일 인스턴스, 재시작 시 세션 소멸 |
| 확장성 | **불가** | 세션·파일이 인스턴스 로컬 |
| 성능 | 미측정 | 관측 수단 없음 |
| 유지보수성 | 양호 | 도메인별 패키지, 명확한 계층 |
| 테스트 용이성 | 낮음 | 테스트 17개, DB 테스트 없음 |
| 관측 가능성 | **매우 낮음** | Actuator 없음, 로그만 |

## QA-001 보안

### 갖춰진 것 `확인됨`

| 항목 | 구현 |
|---|---|
| 비밀번호 해시 | bcrypt (`DelegatingPasswordEncoder`) |
| CSRF 방어 | `csrf().spa()` + 회귀 테스트 3개 |
| 세션 고정 방어 | `changeSessionId()` |
| 동시 세션 제한 | 계정당 1개 |
| 계정 열거 방지 | 메일 API 3종이 202 고정 |
| 토큰 원문 미저장 | SHA-256 해시만 |
| 토큰 1회용·만료 | `isUsable()` 3조건 |
| 경로 탈출 차단 | `startsWith(rootPath)` 검사 |
| SVG 인라인 차단 | `MediaKind.from`이 `FILE`로 강등 |
| 권한의 최종 방어선 | DB UNIQUE·CHECK 제약 |

### 빠진 것 `미결정`

| 항목 | 영향 | 심각도 |
|---|---|---|
| HTTPS 강제 | 세션 쿠키·비밀번호 평문 전송 | **높음** |
| 쿠키 `Secure`·`SameSite` | 쿠키 탈취·CSRF 보조 방어 없음 | **높음** |
| 파일 업로드 인증 | 누구나 100MB 업로드 | **높음** |
| 로그인 시도 제한 | 무차별 대입 무방비 | 높음 |
| 업로드 MIME 내용 검증 | 위장 파일 저장 | 중간 |
| 감사 로그 | 사고 추적 불가 | 중간 |
| 비밀번호 복잡도 | `aaaaaaaa` 통과 | 중간 |
| 운영 CAPTCHA | `fake` 모드면 방어 없음 | **높음** |

상세: [../security/threat-model.md](../security/threat-model.md)

## QA-002 신뢰성

### 갖춰진 것 `확인됨`

| 항목 | 구현 |
|---|---|
| 트랜잭션 경계 | 서비스 계층 `@Transactional` |
| 메일 실패가 가입을 되돌리지 않음 | `AFTER_COMMIT` |
| 외부 호출 타임아웃 | SMTP 5초, CAPTCHA 3초 |
| 미설정 시 폴백 | 메일 로그 전용, CAPTCHA fail-closed |
| 스키마 검증 실패가 삭제로 이어지지 않음 | `clean-disabled: true` |
| 동시성 안전 | 복합 PK + `ON CONFLICT` |
| 카운터 음수 방지 | `WHERE like_count > 0` |

### 빠진 것 `미결정`

| 항목 | 영향 |
|---|---|
| **백업** | 볼륨 손실 = 전체 손실 |
| 메일 발송 재시도 | 일시적 장애도 영구 실패 |
| 메일 발송 결과 기록 | 실패를 사후에 알 수 없음 |
| 헬스체크 | 상태 확인 불가 |
| `depends_on` 조건 | postgres 준비 전 기동 가능 |
| 404 처리 | 정상 상황이 500으로 |

## QA-003 가용성 `확인됨`

| 요인 | 현재 |
|---|---|
| 인스턴스 수 | 1 (구조적 제약) |
| 재시작 시 | **전원 로그아웃** (인메모리 세션) |
| DB 장애 시 | 전면 중단 |
| SMTP 장애 시 | 가입은 되나 인증 불가 |
| CAPTCHA 장애 시 | **가입 전면 중단** (fail-closed) |

CAPTCHA가 fail-closed인 것은 보안상 옳지만 가용성을 희생합니다.
provider 장애가 곧 가입 중단입니다.

목표 가용률은 정하지 않습니다 — 측정 수단도 SLA 요구도 없습니다. `미결정`

## QA-004 확장성 `확인됨`

**현재 구조로는 수평 확장이 불가능합니다.**

| 상태 | 위치 | 2대 이상에서 |
|---|---|---|
| 세션 | 인메모리 | 로그인이 오락가락 |
| 업로드 파일 | 로컬 볼륨 | A에 올린 파일을 B가 못 찾음 |
| 조회수·좋아요 | DB | **문제 없음** |

앞의 둘을 해결하려면:

| 대상 | 필요 |
|---|---|
| 세션 | Spring Session + Redis |
| 파일 | S3 호환 오브젝트 스토리지로 `FileStorageService` 교체 |

`FileStorageService`가 인터페이스가 아니라 구체 클래스인 점이 교체를 어렵게 합니다.
`MailSenderPort`처럼 포트로 추출하는 것이 선행되어야 합니다. `제안`

### 수직 확장의 한계 `제안`

| 병목 후보 | 근거 |
|---|---|
| 키워드 검색 | `LIKE %kw%`가 인덱스를 못 탐. 전체 스캔 |
| `post_view_log` 증가 | 정리 없이 무한 증가 |
| 마이페이지 글 목록 | `member_id` 인덱스 없음 |
| 댓글 목록 | 페이징 없음 |

## QA-005 성능 `미결정`

**측정된 적이 없습니다.** Actuator·Micrometer가 없어 응답 시간을 알 수 없습니다.

### 설계상 유리한 점 `확인됨`

| 항목 | 효과 |
|---|---|
| `@ManyToOne(LAZY)` 일관 사용 | 불필요한 조인 없음 |
| `@OneToMany` 컬렉션 없음 | N+1 원천 차단 |
| 원자적 카운터 UPDATE | 읽기-수정-쓰기 왕복 없음 |
| `idx_post_list` 복합 인덱스 | 목록 조회 최적 |
| 카운터 캐시 (`view_count`, `like_count`) | 집계 쿼리 불필요 |

### 설계상 불리한 점 `확인됨`

| 항목 | 영향 |
|---|---|
| 선행 `%` LIKE 검색 | 전체 스캔 |
| `show-sql: true` | 로그 I/O 비용 |
| 댓글 전체 반환 | 댓글 많은 글에서 응답 폭증 |
| `page.size` 상한 없음 | `?size=100000` 가능 |
| GET이 쓰기 트랜잭션 | 조회수 때문에 읽기 전용 최적화 불가 |

## QA-006 유지보수성 `확인됨`

### 잘 되어 있는 것

| 항목 | 근거 |
|---|---|
| 도메인별 패키지 | 한 기능이 한 디렉터리에 |
| 계층 분리 | `adapter.in` / `application` / `domain` / `adapter.out` |
| 포트-어댑터 | `MailSenderPort`, `CaptchaVerifier` |
| 정적 팩토리 | `Member.pending`, `Post.member/guest` |
| 규칙의 단일 위치 | `Member.normalizeNickname` (결정 D5) |
| 정책을 데이터로 | `board` 테이블 — 배포 없이 변경 |
| 결정 기록 | `PRD.md` 2장, `todo.md` |
| **코드 주석의 질** | 왜 그렇게 했는지가 적혀 있음 |

마지막 항목이 특히 두드러집니다. `SecurityConfig`의 `csrf().spa()` 주석,
`MailConfig`의 폴백 판정 이유, `application.yml`의 `clean-disabled` 경위 등
**함정과 그 배경**이 코드에 남아 있습니다.

### 개선이 필요한 것 `미결정`

| 항목 | 문제 |
|---|---|
| `PostService`가 `AttachmentRepository` 직접 사용 | 도메인 경계 침범. `allowsAttachment` 누락의 원인 |
| `MemberController`가 `MemberRepository` 직접 사용 | 계층 건너뜀 |
| `/api/me` 접두사를 두 컨트롤러가 공유 | 새 엔드포인트 위치가 모호 |
| 미사용 컬럼 3개 | `phone`, `must_change_password`, `temp_password_expires_at` |
| 미사용 enum 값 | `SUSPENDED`, `DELETED` |
| 문서-구현 불일치 | 가입 흐름, 휴대전화 필수 여부 |

## QA-007 테스트 용이성 `확인됨`

| 항목 | 현재 |
|---|---|
| 테스트 클래스 | 5개, 테스트 17개 |
| 단위 테스트 | `MemberTest` (순수 도메인) |
| 통합 테스트 | `SecurityConfigTest`, `MemberControllerTest` 등 |
| DB 테스트 | **없음** |
| 프론트엔드 테스트 | **없음** (`package.json`에 `test` 스크립트 없음) |
| CI | **없음** |

### 테스트하기 쉬운 부분 `확인됨`

`Member.normalizeNickname`처럼 정적 순수 함수는 의존성 없이 테스트됩니다.
`Board.checkReadable`, `Post.checkEditable`도 마찬가지입니다 —
`Optional<Member>`와 `PasswordEncoder`만 있으면 됩니다.

**권한 판정 로직이 도메인에 있는 것이 테스트에 유리합니다.**
아직 테스트가 없을 뿐, 쓰기는 쉽습니다.

### 테스트하기 어려운 부분 `확인됨`

| 대상 | 이유 |
|---|---|
| 조회수 중복 방지 | `ON CONFLICT`가 PostgreSQL 전용 — H2 불가 |
| 부분 UNIQUE 인덱스 | 동일 |
| 메일 실제 발송 | 외부 SMTP 의존 |
| CAPTCHA remote 모드 | 외부 provider 의존 |

앞의 둘은 Testcontainers로 해결 가능합니다.
→ [testing-strategy.md](testing-strategy.md)

## QA-008 관측 가능성 `확인됨`

**가장 취약한 속성입니다.**

| 항목 | 상태 |
|---|---|
| Actuator | 없음 |
| 메트릭 | 없음 |
| 헬스체크 | 없음 |
| 구조화 로그 | 없음 |
| 알림 | 없음 |

CAPTCHA 실패가 로그조차 남지 않는 것이 대표적입니다.
가입이 안 된다는 신고를 받아도 원인을 찾을 수 없습니다.

→ [infrastructure/observability.md](infrastructure/observability.md)

## 우선 개선 제안 `제안`

품질 속성별 위험도와 비용을 함께 고려한 순서입니다.

| 순위 | 작업 | 속성 | 비용 |
|---|---|---|---|
| 1 | Actuator + 헬스체크 | 관측·신뢰성 | 매우 낮음 |
| 2 | DB·업로드 백업 | 신뢰성 | 낮음 |
| 3 | 404 핸들러 추가 | 신뢰성·관측 | 매우 낮음 |
| 4 | 파일 업로드 인증 | 보안 | 낮음 |
| 5 | HTTPS + 쿠키 속성 | 보안 | 중간 (인프라) |
| 6 | 운영 CAPTCHA provider | 보안 | 중간 (결정 필요) |
| 7 | 도메인 권한 테스트 | 테스트 | 낮음 |
| 8 | `show-sql` 환경 변수화 | 보안·성능 | 매우 낮음 |
| 9 | 로그인 시도 제한 | 보안 | 중간 |
| 10 | 세션·파일 외부화 | 확장성 | 높음 |

1·3·8은 각각 몇 줄이면 끝납니다.
10은 실제 확장 필요가 생긴 뒤에 해도 늦지 않습니다.

## 관련 문서

- [../security/security-requirements.md](../security/security-requirements.md)
- [testing-strategy.md](testing-strategy.md)
- [infrastructure/observability.md](infrastructure/observability.md)
- [architecture-overview.md](architecture-overview.md)
