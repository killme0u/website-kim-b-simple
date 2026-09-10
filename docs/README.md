# 문서 색인

이 저장소의 문서는 네 갈래로 나뉩니다. **비즈니스**(왜 만드는가), **기능**(무엇을 하는가),
**기술**(어떻게 만들었는가), **보안**(어떻게 지키는가).

## 근거 상태 표기

모든 문서는 각 항목에 아래 표기를 답니다. 코드에서 확인한 사실과, 아직 정해지지 않은 것을
섞지 않기 위한 장치입니다.

| 표기 | 뜻 |
|---|---|
| `확인됨` | 저장소의 코드·마이그레이션·설정에서 직접 확인한 사실. 근거 경로를 함께 적습니다. |
| `제안` | 이 문서가 처음 제시하는 초안. 팀이 채택하기 전까지는 구속력이 없습니다. |
| `미결정` | 결정이 필요하다고 식별되었으나 아직 정해지지 않은 것. 대부분 `PRD.md` 10장과 `todo.md`에서 승계했습니다. |

`확인됨`이 아닌 항목을 근거로 코드를 고치지 마세요. 먼저 결정을 확정하고 표기를 바꾸는 것이 순서입니다.

## 원본 설계서 (이 트리보다 먼저 존재하던 문서)

아래 세 문서는 이 트리의 **원본**입니다. 서로 어긋나면 아래쪽이 정본입니다.

| 문서 | 내용 | 분량 |
|---|---|---|
| [PRD.md](PRD.md) | 회원제 게시판 아키텍처 설계서. 결정 D1~D5, DDL, 구현 지침, 잔여 미결정 사항 | 2095줄 |
| [UI.md](UI.md) | 화면 명세. 공통 레이아웃, 로그인, 회원가입 단계, CAPTCHA 적용 원칙 | 224줄 |
| [plan.md](plan.md) | UI-백엔드 정합성 및 CAPTCHA 구현 계획, API 계약표, 선결 결정 | - |

저장소 루트의 [`todo.md`](../todo.md)는 미구현·검토 항목의 현재 상태를 담고 있습니다.

## 비즈니스

| 문서 | 내용 |
|---|---|
| [product-vision.md](business/product-vision.md) | 제품이 풀려는 문제와 목표 |
| [business-requirements.md](business/business-requirements.md) | 사업 요구사항 BR-001~ |
| [stakeholders.md](business/stakeholders.md) | 이해관계자와 책임 |
| [glossary.md](business/glossary.md) | 용어 사전 (코드 식별자 ↔ 한국어) |
| [kpi-metrics.md](business/kpi-metrics.md) | 성과 지표 골격 |
| [roadmap.md](business/roadmap.md) | 완료된 Phase와 다음 후보 |
| [policy-rules.md](business/policy-rules.md) | 운영 정책 규칙 (게시판 권한, 계정 상태, 첨부) |
| [compliance-matrix.md](business/compliance-matrix.md) | 규제 대응 현황 골격 |

## 기능

| 문서 | 내용 |
|---|---|
| [feature-catalog.md](features/feature-catalog.md) | 구현된 기능 전체 목록과 코드 위치 |
| [user-journeys.md](features/user-journeys.md) | 비회원·신규회원·기존회원 여정 |
| [use-cases/UC-001-user-signup.md](features/use-cases/UC-001-user-signup.md) | 회원가입 유스케이스 |
| [specifications/FR-001-authentication.md](features/specifications/FR-001-authentication.md) | 인증·인가 기능 명세 |
| [acceptance-criteria.md](features/acceptance-criteria.md) | 인수 기준과 대응 테스트 |
| [state-machines.md](features/state-machines.md) | 회원 상태·토큰 수명주기·게시글 상태 |
| [error-policy.md](features/error-policy.md) | 오류 코드와 HTTP 상태 매핑 |
| [api-behavior.md](features/api-behavior.md) | 엔드포인트별 실제 동작 |

## 기술

| 문서 | 내용 |
|---|---|
| [architecture-overview.md](technology/architecture-overview.md) | 아키텍처 요약과 핵심 결정 |
| [architecture/system-context.md](technology/architecture/system-context.md) | C4 L1 — 시스템 컨텍스트 |
| [architecture/containers.md](technology/architecture/containers.md) | C4 L2 — 컨테이너 |
| [architecture/components.md](technology/architecture/components.md) | C4 L3 — 컴포넌트 |
| [architecture/deployment.md](technology/architecture/deployment.md) | 배포 토폴로지 |
| [architecture/data-flow.md](technology/architecture/data-flow.md) | 주요 데이터 흐름 |
| [adr/ADR-001-modular-monolith.md](technology/adr/ADR-001-modular-monolith.md) | 모듈러 모놀리스 채택 |
| [adr/ADR-002-postgresql.md](technology/adr/ADR-002-postgresql.md) | PostgreSQL 채택 |
| [api/openapi.yaml](technology/api/openapi.yaml) | OpenAPI 3.1 명세 (수기 관리) |
| [api/api-guidelines.md](technology/api/api-guidelines.md) | API 설계 규약 |
| [api/integration-contracts.md](technology/api/integration-contracts.md) | 외부 연동 계약 (SMTP, CAPTCHA) |
| [data/data-model.md](technology/data/data-model.md) | 논리·물리 데이터 모델 |
| [data/erd.md](technology/data/erd.md) | ERD |
| [data/migration-policy.md](technology/data/migration-policy.md) | Flyway 마이그레이션 정책 |
| [data/data-retention.md](technology/data/data-retention.md) | 데이터 보존 정책 골격 |
| [infrastructure/environments.md](technology/infrastructure/environments.md) | 환경 구분과 설정 주입 |
| [infrastructure/ci-cd.md](technology/infrastructure/ci-cd.md) | CI/CD 현황과 제안 |
| [infrastructure/deployment-guide.md](technology/infrastructure/deployment-guide.md) | 배포 절차 |
| [infrastructure/observability.md](technology/infrastructure/observability.md) | 관측 가능성 현황과 제안 |
| [infrastructure/runbooks/](technology/infrastructure/runbooks/README.md) | 장애 대응 런북 |
| [quality-attributes.md](technology/quality-attributes.md) | 품질 속성 |
| [testing-strategy.md](technology/testing-strategy.md) | 테스트 전략 |
| [developer-guide.md](technology/developer-guide.md) | 개발자 가이드 |

## 보안

| 문서 | 내용 |
|---|---|
| [security-requirements.md](security/security-requirements.md) | 보안 요구사항 SR-001~ |
| [threat-model.md](security/threat-model.md) | STRIDE 위협 모델 |
| [security-architecture.md](security/security-architecture.md) | 보안 아키텍처 |
| [authentication-authorization.md](security/authentication-authorization.md) | 인증·인가 상세 |
| [secrets-management.md](security/secrets-management.md) | 비밀값 관리 |
| [data-classification.md](security/data-classification.md) | 데이터 등급 분류 |
| [privacy.md](security/privacy.md) | 개인정보 처리 현황 |
| [vulnerability-management.md](security/vulnerability-management.md) | 취약점 관리 골격 |
| [incident-response.md](security/incident-response.md) | 침해 대응 골격 |
| [access-control.md](security/access-control.md) | 접근 통제 매트릭스 |
| [secure-coding-standard.md](security/secure-coding-standard.md) | 시큐어 코딩 기준 |
| [security-test-plan.md](security/security-test-plan.md) | 보안 테스트 계획 |

## 문서 유지 규칙

1. 코드를 바꾸면 그 코드를 `확인됨`으로 인용한 문서를 같은 변경에서 함께 고칩니다.
2. `미결정`이 결정되면 `PRD.md` 10장과 이 트리의 해당 문서를 동시에 갱신합니다.
3. 새 API를 추가하면 [openapi.yaml](technology/api/openapi.yaml)과 [api-behavior.md](features/api-behavior.md)를 함께 갱신합니다.
4. 새 Flyway 마이그레이션을 추가하면 [data-model.md](technology/data/data-model.md)와 [erd.md](technology/data/erd.md)를 함께 갱신합니다.
