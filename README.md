# 익명/회원 게시판 통합 플랫폼 (Simple Board)

본 프로젝트는 React 프론트엔드와 Spring Boot 백엔드가 통합된 단순하지만 확장성 있는 다목적 자유게시판 시스템입니다.

## 기능 스펙
* **공통**: Spring Boot 단일 포트(8080) 정적 리소스(SPA 라우팅) 응답 구성
* **Phase 1~2 (Domain & Auth)**: JPA 기반 도메인 설계, Spring Security를 이용한 Session/Cookie 방식의 로그인 및 게스트 권한 분기
  * SPA용 CSRF 구성(`csrf().spa()`) — 매 응답에 `XSRF-TOKEN` 쿠키를 내려주고 `X-XSRF-TOKEN` 헤더의 원본 토큰을 검증
  * 회원가입 인증·비밀번호 재설정·아이디 찾기 메일 발송 (Thymeleaf HTML 템플릿)
* **Phase 3~5 (Board & Post & Comment)**: 
  * 게시판 속성(allowsComment, authToRead 등)에 따른 CRUD 권한 검증 및 페이지네이션
  * 게시물 및 댓글 `ON CONFLICT DO NOTHING` 기반 원자적 조회수 증가 기능
  * 로컬 스토리지를 이용한 파일 업로드 및 다운로드 기능 (보안 취약점 방어)
* **Phase 6~7 (React & UX)**:
  * React, Vite, Tailwind CSS, COSS UI, Zustand 사용
  * 마크다운 렌더링 지원 (`react-markdown`, `@tailwindcss/typography`)
  * SPA 라우팅 및 폼 기반 로그인 연동

## 기술 스택
- **Backend**: Java 25, Spring Boot 4.1.1, Spring Data JPA, Spring Security, Flyway
- **Frontend**: TypeScript, React 19, Vite, Tailwind CSS 3, COSS UI, Zustand
- **Database**: PostgreSQL 15

### 프론트엔드 - COSS UI

```shell
cd frontend-react
npx shadcn@latest init @coss/style
```


## 시작하기

빠르게 로컬 환경에서 테스트하고 싶다면 Docker Compose를 이용할 수 있습니다.

### Docker Compose 환경에서 실행 (권장)
```bash
# 컨테이너 빌드 및 백그라운드 실행
docker compose up -d --build

# 접속
# http://localhost:8080/
```

### 수동 데브 서버 실행 (Backend + Frontend 분리)
1. DB 구동:
```bash
# Postgres 단일 컨테이너 구동
docker compose up -d postgres
```
2. Spring 백엔드 애플리케이션 시작:
```bash
./gradlew :backend-springboot:bootRun
# 백엔드가 포트 8080에서 리스닝 상태가 됩니다.
```
3. 프론트엔드 React 독립 실행 (옵션):
```bash
cd frontend-react
npm run dev
# 포트 5173에 Vite dev server가 열리며 8080으로 프록시(Proxy)합니다.
```

## 메일 발송 설정

SMTP 계정 정보와 CAPTCHA secret 같은 비밀값은 **저장소 루트의 `.env`** 에서 주입합니다.
`.env`는 `.gitignore` 대상이라 커밋되지 않으며, 커밋되는 것은 양식인 `.env.example` 뿐입니다.

```powershell
Copy-Item .env.example .env   # 저장소 루트에서 1회
# 그리고 .env 에 실제 값을 채우면 끝
```

`.env`는 dotenv가 아니라 **`.properties` 문법**으로 읽힙니다.
값을 따옴표로 감싸지 말고, 역슬래시 대신 `/`를 쓰고, 값에 한글을 넣지 마세요(ISO-8859-1로 읽힘).

| 키 | 설명 |
|---|---|
| `MAIL_SMTP_HOST` | SMTP 호스트. 비우면 실제 발송 대신 로그로만 남습니다 |
| `MAIL_SMTP_PORT` | 기본 `465` |
| `MAIL_SMTP_SSL` / `MAIL_SMTP_STARTTLS` | `465`면 `true`/`false`, `587`이면 `false`/`true` |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | 로그인 계정. 네이버는 **앱 비밀번호**를 발급받아 씁니다 |
| `MAIL_FROM_ADMIN` | 보내는 사람 주소. 네이버는 보통 `MAIL_USERNAME`과 같은 주소만 허용합니다 |
| `MAIL_DEBUG` | SMTP 대화를 stdout에 출력. 인증 정보까지 남으므로 디버깅할 때만 `true` |
| `APP_BASE_URL` | 메일 본문 링크의 기준 주소 |
| `CAPTCHA_*` | CAPTCHA provider 설정 |

`MAIL_SMTP_HOST`나 `MAIL_USERNAME` 중 하나라도 비어 있으면 `LoggingMailSender`가
메일 본문(인증 링크 포함)을 애플리케이션 로그에만 남깁니다.
덕분에 개발 환경에서는 별도 SMTP 없이도 가입 흐름을 끝까지 확인할 수 있고,
`.env.example`을 복사만 해 둔 상태에서 가입 시점에 SMTP 인증 오류로 터지지도 않습니다.

읽는 경로는 `application.yml`의 `spring.config.import`이며 `optional:`이라 `.env`가 없어도 앱은 그대로 뜹니다.
`bootRun`은 작업 디렉터리가 모듈 디렉터리라서 `backend-springboot/build.gradle`이 절대경로를 함께 넘깁니다.
테스트는 로컬 값에 흔들리지 않도록 일부러 `.env`를 읽지 않습니다.

같은 이름의 **OS 환경 변수가 있으면 그쪽이 이깁니다**(운영·컨테이너 배포 경로).
`docker compose`는 저장소 루트의 `.env`를 자동으로 읽어 컨테이너 환경 변수로 넘기며,
이미지 안에는 `.env`를 넣지 않습니다.

## 게시판 접근 정책

| slug | 이름 | 읽기 | 쓰기 | 댓글 | 첨부 |
|---|---|---|---|---|---|
| `free` | 자유게시판 | 누구나 | 누구나(작성자 비밀번호) | ✗ | ✗ |
| `qna` | Q&A 게시판 | **로그인 필요** | 로그인 필요 | ● | ✗ |
| `archive` | 자료실 | **로그인 필요** | 로그인 필요 | ✗ | ● |

정책은 `board` 테이블의 컬럼 값이며 코드 분기가 아닙니다.
읽기 권한을 바꾸려면 `requires_auth_to_read`를 갱신하는 마이그레이션만 추가하면 됩니다.

## 비고
* 기본 데이터베이스 초기화를 위해 `[V2__seed_board.sql]`가 구성되어 있으며 시작 시 Flyway가 자동 수행하여 `free`, `qna`, `archive` 세 개의 게시판이 생성됩니다.
* `[V4__member_only_board_read.sql]`는 Q&A·자료실을 회원 전용(읽기에도 로그인 필요)으로 전환합니다.