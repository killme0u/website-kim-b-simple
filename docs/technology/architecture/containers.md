# 컨테이너 (C4 Level 2)

> 상태: `확인됨` — `docker-compose.yml`, `Dockerfile`, Gradle 설정에서 도출.

## 런타임 구성 `확인됨`

```
┌───────────────────────────────────────────────────────────┐
│  Docker Compose                                           │
│                                                           │
│  ┌─────────────────────────────┐   ┌────────────────────┐ │
│  │  app                        │   │  postgres          │ │
│  │  eclipse-temurin:25-jre     │   │  postgres:15-alpine│ │
│  │                             │   │                    │ │
│  │  ┌───────────────────────┐  │   │  board_db          │ │
│  │  │  app.war (bootWar)    │  │   │  board_user        │ │
│  │  │                       │  │   │                    │ │
│  │  │  ├ Spring MVC + Sec.  │  │◄──┤  :5432             │ │
│  │  │  ├ JPA/Hibernate      │  │   │                    │ │
│  │  │  ├ Flyway             │  │   └─────────┬──────────┘ │
│  │  │  └ static/ (SPA 빌드) │  │             │            │
│  │  └───────────────────────┘  │             ▼            │
│  │                             │      ┌─────────────┐     │
│  │  :8080                      │      │ board_data  │ vol │
│  │  /app/uploads ──────────────┼──►   └─────────────┘     │
│  └─────────────────────────────┘      ┌─────────────┐     │
│              │                        │board_uploads│ vol │
│              └────────────────────────┴─────────────┘     │
└───────────────────────────────────────────────────────────┘
                     │
                     ▼  :8080 호스트 노출
                  브라우저
```

## 컨테이너 상세

### app `확인됨`

| 항목 | 값 |
|---|---|
| 빌드 | 저장소 루트 `Dockerfile` |
| 베이스(빌드) | `eclipse-temurin:25.0.3_9-jdk` |
| 베이스(실행) | `eclipse-temurin:25.0.3_9-jre` |
| 아티팩트 | `app.war` |
| 진입점 | `java -jar app.war` |
| 포트 | 8080 |
| 볼륨 | `board_uploads:/app/uploads` |

환경 변수 (`docker-compose.yml`)

| 변수 | 값 |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/board_db` |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | `board_user` / `board_password` |
| `APP_STORAGE_ROOT` | `/app/uploads` |
| `MAIL_*` | 호스트 `.env`에서 전달 (없으면 빈 문자열) |
| `APP_BASE_URL` | 기본 `http://localhost:8080` |

**`.env`는 이미지에 들어가지 않습니다.** `Dockerfile`이 COPY하지 않고,
compose가 호스트의 `.env`를 읽어 환경 변수로 전달합니다.

### postgres `확인됨`

| 항목 | 값 |
|---|---|
| 이미지 | `postgres:15-alpine` |
| 컨테이너명 | `board-postgres` |
| 포트 | 5432 (호스트에 노출) |
| 볼륨 | `board_data:/var/lib/postgresql/data` |

**자격 증명이 `docker-compose.yml`에 평문으로 있습니다** (`board_user`/`board_password`).
로컬 개발용이라 그대로 두었으나 운영에서는 바꿔야 합니다. `미결정`
5432가 호스트에 노출되어 있어 같은 네트워크의 다른 기기에서 접근 가능합니다.

## 빌드 파이프라인 `확인됨`

Gradle 멀티모듈이 프론트엔드 산출물을 백엔드 리소스로 흘려보냅니다.

```
settings.gradle
  ├── :backend-springboot
  └── :frontend-react

:frontend-react
   npm run build → build/dist
   └── frontendAssets 라는 이름의 Gradle configuration 으로 노출

:backend-springboot
   dependencies { frontendAssets project(path: ':frontend-react', configuration: 'frontendAssets') }
   processResources { from(configurations.frontendAssets) { into 'static' } }
   └── src/main/resources/static/ 위치에 SPA가 들어감

bootWar → app.war (SPA 포함)
```

백엔드를 빌드하면 프론트엔드가 자동으로 먼저 빌드됩니다.
두 모듈을 따로 배포할 수 없는 대신, 버전 불일치가 구조적으로 불가능합니다.

### Dockerfile의 COPY 목록 `확인됨`

레이어 캐시를 위해 의존성 파일을 먼저 복사합니다.

```
1. gradlew, gradle/, build.gradle, settings.gradle
2. 각 모듈의 build.gradle
3. frontend-react/package.json, package-lock.json
4. 소스 (backend src, frontend src)
5. frontend 설정 (vite.config.ts, tsconfig*.json, index.html, public/)
6. RUN ./gradlew build -x test
```

**주의**: `Dockerfile`의 COPY 목록은 프론트엔드 파일 구성과 함께 움직입니다.
새 설정 파일을 추가하면 여기에도 추가해야 합니다.
`public/`을 빠뜨리면 `index.html`이 참조하는 `/favicon.svg`가 404가 됩니다
(Dockerfile 주석에 기록되어 있음).

**테스트를 건너뜁니다** (`-x test`). 이미지 빌드가 테스트를 검증하지 않습니다. `미결정`

### `.dockerignore` `확인됨`

빌드 컨텍스트를 줄이는 파일입니다. 파일 상단에 경고가 있습니다.

> 여기에 넣은 경로는 COPY 할 수 없게 된다.
> Dockerfile 이 COPY 하는 경로는 절대 넣지 말 것.

## 개발 환경의 다른 구성 `확인됨`

컨테이너 없이 두 프로세스를 따로 띄우는 방식입니다.

```
브라우저 :5173
    │
    ▼
Vite dev server (:5173)
    │  proxy: '/api' → http://localhost:8080
    ▼
Spring Boot bootRun (:8080)
    │
    ▼
PostgreSQL (PGSQL_HOST:PGSQL_PORT)
```

(`frontend-react/vite.config.ts:17-20`)

### CORS가 아니라 프록시인 이유 `확인됨`

브라우저 입장에서 **같은 오리진**(`localhost:5173`)이 유지됩니다.
CORS를 쓰면 교차 출처가 되어 쿠키(`JSESSIONID`, `XSRF-TOKEN`) 전달에
`SameSite`·`credentials` 설정이 추가로 필요해집니다.
프록시는 그 문제를 통째로 없앱니다.

### `bootRun`의 `.env` 처리 `확인됨`

```gradle
tasks.named('bootRun') {
    def dotenv = rootProject.file('.env').absolutePath.replace('\\', '/')
    systemProperty 'spring.config.import', "optional:file:${dotenv}[.properties]"
}
```

`bootRun`의 작업 디렉터리가 모듈 디렉터리라 상대 경로가 헷갈리므로
**절대 경로**를 직접 넘깁니다. `test`에는 넘기지 않습니다 —
테스트가 개발자 로컬 `.env` 값에 흔들리면 안 되기 때문입니다.

## SPA 라우팅 처리 `확인됨`

운영에서는 Spring이 정적 리소스를 직접 서빙합니다.

```java
registry.addResourceHandler("/**")
    .addResourceLocations("classpath:/static/")
    .resourceChain(true)
    .addResolver(new PathResourceResolver() {
        protected Resource getResource(String path, Resource location) {
            if (path.startsWith("api/")) return null;      // API는 컨트롤러로
            Resource requested = location.createRelative(path);
            return (requested.exists() && requested.isReadable())
                ? requested
                : new ClassPathResource("static/index.html");  // 딥링크 폴백
        }
    });
```

(`config/SpaResourceConfig.java`)

| 요청 | 결과 |
|---|---|
| `/api/boards` | `null` 반환 → 컨트롤러가 처리 |
| `/favicon.svg` | 실제 파일 |
| `/boards/qna` | 파일 없음 → `index.html` (React Router가 처리) |

`SecurityConfigTest.spaShellIsReachableAnonymously`가 이 경로를 검증합니다.

## 확장 시 깨지는 것 `미결정`

app 컨테이너를 2개 이상으로 늘리면:

| 항목 | 문제 |
|---|---|
| 세션 | 인메모리라 인스턴스 간 공유 안 됨 → 로그인이 오락가락 |
| 업로드 파일 | 로컬 볼륨이라 A에 올린 파일을 B가 못 찾음 |
| 조회수·좋아요 | DB 기반이라 **문제 없음** |

앞의 둘을 해결하려면 Spring Session(Redis)과 오브젝트 스토리지가 필요합니다.

## 관련 문서

- [components.md](components.md) — C4 L3
- [deployment.md](deployment.md) — 배포 토폴로지
- [../infrastructure/deployment-guide.md](../infrastructure/deployment-guide.md) — 배포 절차
- [../infrastructure/environments.md](../infrastructure/environments.md) — 환경별 설정
