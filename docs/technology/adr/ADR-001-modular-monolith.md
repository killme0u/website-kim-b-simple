# ADR-001 모듈러 모놀리스와 단일 배포 단위

> 상태: **채택됨** `확인됨` — 코드와 빌드 설정에 반영되어 있습니다.
> 이 ADR은 이미 내려진 결정을 사후에 기록한 것입니다(코드가 먼저, 문서가 나중).

## 맥락

게시판 시스템을 React 프론트엔드와 Spring Boot 백엔드로 만들되,
운영 부담을 최소화해야 했습니다.

제약:

- 개발 인원이 소수 (git 이력상 1인)
- 별도 인프라 팀 없음
- 작업 PC가 여러 곳 (회사·집·학원)으로 환경이 자주 바뀜
- 서비스 규모가 크지 않음

## 검토한 선택지

### A. 프론트·백엔드 분리 배포

React를 정적 호스팅(CDN·Nginx)에, Spring Boot를 별도 서버에 배포.

| 장점 | 단점 |
|---|---|
| 각각 독립 배포 | CORS 설정 필요 |
| 프론트엔드 CDN 캐싱 | 쿠키 인증이 교차 출처가 되어 복잡해짐 |
| 스케일링 독립 | 배포 대상이 둘 |
| | 버전 불일치 가능 |

### B. 단일 WAR에 SPA 내장 (채택)

Gradle이 프론트엔드를 빌드해 백엔드 리소스로 넣고, 포트 하나로 서빙.

| 장점 | 단점 |
|---|---|
| 배포 대상이 하나 | 프론트엔드만 고쳐도 전체 재빌드 |
| 같은 오리진 — CORS 불필요 | 정적 자산 CDN 활용 어려움 |
| 버전 불일치가 구조적으로 불가능 | 두 기술 스택이 한 빌드에 묶임 |
| 쿠키 인증이 단순 | |

### C. 마이크로서비스

회원·게시판·첨부를 별도 서비스로 분리.

규모 대비 과도합니다. 서비스 간 통신, 분산 트랜잭션, 서비스 디스커버리가
현재 요구사항에 없는 복잡도를 더합니다. 검토에서 제외했습니다.

## 결정

**B를 채택합니다.** 단일 Gradle 멀티모듈 저장소에서 SPA를 빌드해
Spring Boot WAR에 내장하고, 도메인별 패키지로 내부 모듈성을 유지합니다.

### 구현 `확인됨`

```gradle
// frontend-react/build.gradle 이 build/dist 를 frontendAssets 로 노출
// backend-springboot/build.gradle
configurations {
    frontendAssets { canBeConsumed = false; canBeResolved = true }
}
dependencies {
    frontendAssets project(path: ':frontend-react', configuration: 'frontendAssets')
}
tasks.named('processResources') {
    from(configurations.frontendAssets) { into 'static' }
}
```

Gradle 산출물 공유이므로 백엔드 빌드가 프론트엔드 빌드를 자동으로 트리거합니다.

### 내부 모듈성 `확인됨`

배포는 하나지만 코드는 도메인별로 나뉩니다.

```
member/  board/  post/  comment/  attachment/  captcha/
  각각 adapter.in.web / application / domain / (adapter.out)
```

기술 계층(`controller/`, `service/`)이 아니라 도메인이 최상위입니다.
나중에 분리해야 할 때 경계선이 이미 그어져 있습니다.

## 결과

### 얻은 것 `확인됨`

| 항목 | 근거 |
|---|---|
| `docker compose up -d --build` 한 줄 배포 | `docker-compose.yml` |
| CORS 설정 없음 | `SecurityConfig`에 CORS 구성 없음 |
| 개발에서도 같은 오리진 | Vite 프록시 (`vite.config.ts:19`) |
| 버전 불일치 불가능 | 한 아티팩트 |
| SPA 딥링크 동작 | `SpaResourceConfig` 폴백 |

### 치른 대가 `확인됨`

| 항목 | 영향 |
|---|---|
| 프론트엔드만 고쳐도 전체 빌드 | Gradle + npm 전체 사이클 |
| Docker 빌드에 JDK 필요 | 빌드 스테이지가 큼 |
| `Dockerfile` COPY 목록 유지보수 | 프론트엔드 파일 구성과 함께 움직임 |
| 정적 자산 CDN 어려움 | 모든 요청이 앱 서버로 |

`Dockerfile`에 이 대가가 주석으로 기록되어 있습니다 —
Tailwind 4로 옮기며 설정 파일이 사라졌을 때, `public/`을 빠뜨려
`favicon.svg`가 404가 났을 때 모두 COPY 목록 문제였습니다.

### WAR 패키징의 부수 효과 `확인됨`

`war` 플러그인 때문에 `bootJar`가 아니라 `bootWar`가 만들어집니다.
`build/libs`에 세 파일이 남고 실행 가능한 것은 하나뿐입니다.

| 파일 | 실행 |
|---|---|
| `backend-springboot-0.0.1-SNAPSHOT.war` | 가능 |
| `...-plain.war` | 불가 |
| `...-plain.jar` | 불가 |

`Dockerfile`이 정확히 첫 번째만 COPY하며, 그 이유가 주석에 적혀 있습니다.
헷갈리기 쉬운 지점이라 기록해 둔 것입니다.

## 이 결정이 막고 있는 것 `미결정`

단일 배포 자체보다 **상태를 인스턴스 안에 들고 있는 것**이 문제입니다.

| 상태 | 위치 | 확장 시 |
|---|---|---|
| 세션 | 인메모리 | 인스턴스 간 공유 안 됨 |
| 업로드 파일 | 로컬 볼륨 | A에 올린 파일을 B가 못 찾음 |
| 조회수·좋아요 | DB | 문제 없음 |

app을 2대로 늘리는 순간 앞의 둘이 깨집니다.
이는 ADR-001의 필연적 결과가 아니라 **별도의 미결정 사항**입니다 —
단일 WAR로도 세션을 Redis에, 파일을 오브젝트 스토리지에 둘 수 있습니다.

## 재검토 조건 `제안`

아래 중 하나가 발생하면 이 결정을 다시 봐야 합니다.

- 인스턴스를 2대 이상으로 늘려야 함 → 먼저 세션·파일 외부화
- 프론트엔드 빌드 시간이 개발 흐름을 방해할 정도
- 프론트엔드와 백엔드를 다른 팀이 다른 주기로 배포
- 정적 자산 CDN 캐싱이 성능 요구사항이 됨

현재는 어느 것도 해당하지 않습니다.

## 관련 문서

- [ADR-002-postgresql.md](ADR-002-postgresql.md)
- [../architecture/containers.md](../architecture/containers.md) — 빌드 파이프라인
- [../architecture/deployment.md](../architecture/deployment.md) — 확장 시 필요한 변경
- [../architecture-overview.md](../architecture-overview.md)
