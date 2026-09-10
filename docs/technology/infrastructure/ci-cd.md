# CI/CD

> 상태: **현황은 `확인됨` — CI/CD가 없습니다.** 제안은 `제안`입니다.

## 현황 `확인됨`

| 항목 | 상태 |
|---|---|
| GitHub Actions 워크플로 | **없음** — `.github`에 `copilot-instructions.md`만 있음 |
| 다른 CI (Jenkins, GitLab CI 등) | 없음 |
| 자동 테스트 실행 | 없음 |
| 자동 빌드 | 없음 |
| 자동 배포 | 없음 |
| 이미지 레지스트리 푸시 | 없음 |
| 브랜치 보호 규칙 | 확인 불가 (저장소 설정) |

**모든 검증이 수동입니다.**

## 현재 수동 절차 `확인됨`

`plan.md`의 검증 기준이 사실상의 체크리스트입니다.

```bash
# 프론트엔드
cd frontend-react
npm run lint      # oxlint
npm run build     # tsc -b && vite build

# 백엔드
./gradlew :backend-springboot:test
./gradlew build

# 통합
docker compose up -d --build
curl -i http://localhost:8080/
curl -i http://localhost:8080/favicon.svg
curl -i http://localhost:8080/api/boards
```

사람이 기억해서 실행해야 하므로 빠뜨리기 쉽습니다.

### Docker 빌드가 테스트를 건너뛴다 `확인됨`

```dockerfile
RUN chmod +x ./gradlew && ./gradlew build -x test
```

`-x test`가 있습니다. **이미지 빌드가 테스트를 검증하지 않습니다.**
테스트가 깨진 채로 이미지가 만들어집니다.

빌드 시간을 줄이려는 선택으로 보이지만, CI가 없는 상태에서는
테스트가 실행되는 곳이 아무 데도 없게 됩니다. `미결정`

## 제안하는 CI `제안`

### 최소 구성 — PR 검증

가장 큰 가치를 가장 낮은 비용으로 얻는 구성입니다.

```yaml
# .github/workflows/ci.yml  (제안 — 아직 존재하지 않음)
name: CI

on:
  pull_request:
  push:
    branches: [main]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
          cache: gradle
      - run: ./gradlew :backend-springboot:test

  frontend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: frontend-react
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: npm
          cache-dependency-path: frontend-react/package-lock.json
      - run: npm ci
      - run: npm run lint
      - run: npm run build

  docker:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: docker build -t board:ci .
```

**주의**: 백엔드 테스트가 `.env`를 읽지 않도록 되어 있으므로(`build.gradle`)
CI에서 추가 설정 없이 돌아갑니다. 현재 테스트 5개는 DB도 쓰지 않습니다.

### 왜 이 순서인가 `제안`

| 작업 | 소요 | 잡아내는 것 |
|---|---|---|
| `npm run lint` | 초 단위 | 문법·스타일 |
| `npm run build` | 수십 초 | **타입 오류** (`tsc -b`) |
| `./gradlew test` | 수십 초 | 회귀 (CSRF, 닉네임 정규화, 메일 폴백) |
| `docker build` | 수 분 | **Dockerfile COPY 목록 누락** |

`docker build`가 특히 가치 있습니다.
`Dockerfile`의 COPY 목록은 프론트엔드 파일 구성과 함께 움직이는데,
이 프로젝트에서 실제로 두 번 문제가 됐습니다
(Tailwind 4 설정 파일 삭제, `public/` 누락).

### 다음 단계 `제안`

| 단계 | 내용 | 선행 조건 |
|---|---|---|
| 통합 테스트 | Testcontainers로 실제 PostgreSQL | 조회수·권한 테스트 작성 |
| 컨테이너 스모크 테스트 | compose 기동 후 3개 URL 확인 | 헬스체크 추가 |
| 이미지 푸시 | 레지스트리 결정 | 배포 대상 확정 |
| 자동 배포 | 배포 환경 확정 | **운영 환경 미정의** |

자동 배포는 배포 대상이 정해진 뒤에 의미가 있습니다. 현재는 정의되지 않았습니다.

## 배포 파이프라인 `미결정`

**운영 배포 대상이 정의되어 있지 않습니다.**

저장소에 없는 것:

- 배포 서버·클러스터 정보
- 이미지 레지스트리
- 배포 스크립트
- 롤백 절차
- 환경별 시크릿 주입 방식

`docker-compose.yml`은 로컬 개발용입니다 —
DB 자격 증명이 평문이고 5432가 호스트에 노출되어 있습니다.

## 릴리스 관리 `확인됨`

| 항목 | 상태 |
|---|---|
| 버전 | `0.0.1-SNAPSHOT` 고정 (`build.gradle`) |
| 태그 | git 태그 없음 |
| 변경 로그 | `todo.md`가 일부 역할 |
| 브랜치 전략 | `main` 단일 브랜치 |

버전이 `SNAPSHOT`에 고정되어 있어 어떤 이미지가 어떤 코드인지 구분할 수 없습니다.
배포를 시작하면 커밋 SHA나 태그를 이미지 태그로 쓰는 것이 필요합니다. `제안`

## 품질 게이트 `제안`

CI를 도입한다면 아래를 **차단 조건**으로 삼는 것을 권합니다.

| 게이트 | 근거 |
|---|---|
| 백엔드 테스트 통과 | 회귀 방지 (CSRF, 닉네임 D5) |
| `tsc -b` 통과 | 타입 오류가 런타임까지 가지 않게 |
| `docker build` 성공 | COPY 목록 누락 방지 |
| `oxlint` 통과 | 일관성 |

**커버리지 기준은 아직 이르다고 봅니다.** 현재 테스트가 5개 클래스뿐이라
기준을 세우면 통과할 수 없거나 의미 없는 테스트를 양산하게 됩니다.
먼저 [../../features/acceptance-criteria.md](../../features/acceptance-criteria.md)의
우선 보강 항목을 채우는 편이 낫습니다.

## 보안 스캔 `제안`

| 대상 | 도구 예 | 우선순위 |
|---|---|---|
| 의존성 취약점 | Dependabot, `gradle dependencyCheck` | 높음 |
| 비밀값 커밋 | gitleaks, GitHub secret scanning | **높음** |
| 컨테이너 이미지 | Trivy | 중간 |
| 정적 분석 | CodeQL | 중간 |

비밀값 스캔이 특히 중요합니다 —
`.env`가 `.gitignore` 대상이지만 실수로 커밋될 수 있고,
`docker-compose.yml`에는 이미 DB 비밀번호가 평문으로 들어 있습니다.

## 도입 순서 `제안`

| 순위 | 작업 | 비용 | 효과 |
|---|---|---|---|
| 1 | PR 검증 워크플로 (test + build + lint) | 낮음 | 회귀 방지 |
| 2 | `docker build` 검증 | 낮음 | COPY 누락 방지 |
| 3 | Dependabot | 매우 낮음 | 의존성 취약점 |
| 4 | secret scanning | 매우 낮음 | 비밀값 유출 |
| 5 | Testcontainers 통합 테스트 | 중간 | DB 로직 검증 |
| 6 | 배포 자동화 | 높음 | **배포 대상 확정 후** |

1~4는 각각 파일 하나 추가로 끝납니다.

## 관련 문서

- [../testing-strategy.md](../testing-strategy.md) — 무엇을 테스트할 것인가
- [../../features/acceptance-criteria.md](../../features/acceptance-criteria.md) — 검증 기준
- [deployment-guide.md](deployment-guide.md) — 수동 배포 절차
- [../../security/vulnerability-management.md](../../security/vulnerability-management.md)
