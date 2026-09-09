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

인증 메일은 `spring.mail.host`가 설정된 경우에만 실제로 발송됩니다.
설정이 없으면 `LoggingMailSender`가 메일 본문(인증 링크 포함)을 애플리케이션 로그에만 남기므로,
개발 환경에서는 별도 SMTP 없이도 가입 흐름을 끝까지 확인할 수 있습니다.

```bash
export SPRING_MAIL_HOST=smtp.example.com
export SPRING_MAIL_PORT=587
export SPRING_MAIL_USERNAME=board
export SPRING_MAIL_PASSWORD=...
export MAIL_FROM="BoardSystem <no-reply@example.com>"
export APP_BASE_URL=https://board.example.com   # 메일 본문 링크의 기준 주소
```

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