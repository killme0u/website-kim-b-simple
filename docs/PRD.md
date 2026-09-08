# 회원제 게시판 아키텍처 설계서

## 기술 스택

| 영역 | 기술 |
|---|---|
| 백엔드 | Spring Boot 4.1.1, Spring Security 7, Spring Data JPA, Jakarta Mail |
| 데이터베이스 | PostgreSQL, Flyway (마이그레이션) |
| 프론트엔드 | React 19.2, TanStack Query v5, Zustand v5, Vite, COSS UI (DaisyUI 교체 예정) |
| 빌드 | Gradle 멀티 프로젝트 (Groovy DSL) |
| 개발 환경 | IntelliJ IDEA (백엔드), WebStorm (프론트엔드) |

## 프로젝트 식별자

| 항목 | 값 |
|---|---|
| Gradle 루트 프로젝트명 | `website-kim-b-simple` |
| 백엔드 서브프로젝트 | `backend-springboot` |
| 프론트엔드 서브프로젝트 | `frontend-react` |
| 백엔드 기준 패키지 | `page.sanotehu.board.backend` |
| 프론트엔드 패키지명 | `page.sanotehu.board.frontend` (`package.json`의 `name`) |

---

# 변경 이력 (v1.0 → v2.0)

## 확정된 결정

| # | 항목 | 결정 내용 | 반영 위치 |
|---|---|---|---|
| D1 | 자유게시판 수정·삭제 권한 | 작성 시 **작성자명 + 작성자 비밀번호** 입력, 수정·삭제 시 비밀번호 확인. **관리자는 비밀번호 없이 가능** | 2.1, 3.3, 4.1, 6.6 |
| D2 | 조회수 중복 방지 | **하이브리드** — 로그인 사용자는 서버 사이드 기록, 익명 사용자는 쿠키 | 2.6, 4.1, 6.2 |
| D3 | 게시판 모델링 | `Post` 단일 엔티티 + `Board`가 정책 보유 | 2.8, 3.2 |
| D4 | 첨부파일 보안 | 업로드 보안 체크리스트 8항목을 **구현 필수 항목**으로 승격 | 6.3 |

## 함께 수정한 정합성 문제

| # | 문제 | v1.0 | v2.0 |
|---|---|---|---|
| F1 | `Post.checkEditable()`이 비회원 글에 대해 관리자 권한을 확인하지 않음 — D1과 모순 | 비회원 글은 비밀번호만 검사 | 관리자 우회 경로를 최우선으로 배치 |
| F2 | 엔티티가 `PasswordEncoder`를 직접 참조 (주입 경로 없음) | `encoder.matches(...)` | 메서드 파라미터로 주입 |
| F3 | `backend-springboot/build.gradle`이 존재하지 않는 `:frontend` 참조 | `project(path: ':frontend', ...)` | `:frontend-react` |
| F4 | 루트 디렉터리명과 `rootProject.name` 불일치, 패키지 경로에 `backend` 누락 | `board/`, `page/sanotehu/board/` | `website-kim-b-simple/`, `page/sanotehu/board/backend/` |

## 신규 추가

- **4.1** `post_view_log` 테이블 — 로그인 사용자의 일별 조회 기록
- **3.2 / 4.1** `board.requires_auth` → `requires_auth_to_read` + `requires_auth_to_write`로 분리
- **5.5** API 엔드포인트 목록
- **6.2** 조회수 추적 전략(`PostViewTracker`) 전면 재설계
- **6.6** 비회원 글 권한 검증 절차

---

# 목차

1. [요구사항 정리](#1-요구사항-정리)
2. [요구사항 검토 — 결정 사항](#2-요구사항-검토--결정-사항)
3. [도메인 모델](#3-도메인-모델)
4. [데이터베이스 스키마](#4-데이터베이스-스키마)
5. [백엔드 구성](#5-백엔드-구성)
6. [핵심 기능 구현 지침](#6-핵심-기능-구현-지침)
7. [프론트엔드 구성](#7-프론트엔드-구성)
8. [개발 환경 구성](#8-개발-환경-구성)
9. [구현 순서](#9-구현-순서)
10. [잔여 미결정 사항](#10-잔여-미결정-사항)
11. [부록 — 상태 관리 판단 트리](#11-부록--상태-관리-판단-트리)

---

# 1. 요구사항 정리

## 1.1 회원 인증

- 회원가입 / 로그인 / 로그아웃 / 회원정보수정
- 가입 항목: 아이디, 비밀번호, 이름, 이메일, 전화번호
- 아이디 중복확인, 비밀번호 확인
- 회원가입 시 이메일 인증
- 회원가입 시 약관 동의 및 CAPTCHA 서버 검증
- 이메일 인증 완료·재발송, 비밀번호 찾기·재설정

## 1.2 게시판

### 자유게시판 (비회원제)

- 목록 / 읽기 / 쓰기 / 수정 / 삭제
- 파일 첨부 없음
- **작성 시 작성자명 + 작성자 비밀번호 입력** (D1)
- **수정·삭제 시 비밀번호 확인, 관리자는 비밀번호 없이 가능** (D1)

### Q&A 게시판 (회원제)

- CRUD 기본 기능
- 댓글 기능 (열람 페이지 하단), 댓글 입력 / 수정 / 삭제

### 자료실 게시판 (회원제)

- 첨부파일 타입에 따른 출력 분기
    - 이미지(png, gif, jpg 등) → `<img>`
    - 동영상(mp4, avi 등) → `<video>`
    - 음원(mp3 등) → `<audio>`
    - 그 외 → 다운로드 링크만

### 공통

- 페이징 (목록 하단 페이지 번호)

## 1.3 추가 기능

- **조회수 중복 방지** — 게시물 조회수는 하루에 한 번만 증가 (D2)
    - 로그인 사용자 → 서버 사이드 기록 기반
    - 익명 사용자 → 쿠키 기반
- **쿠키 — 아이디 저장** — 로그인 페이지에서 아이디 기억
- **비밀번호 찾기** — 이메일로 임시 비밀번호 발송, 로그인 후 비밀번호 변경
- **좋아요** — 게시물 읽기에서 좋아요 클릭 시 1 증가

---

# 2. 요구사항 검토 — 결정 사항

## 2.1 자유게시판의 수정·삭제 권한 — **결정됨 (D1)**

로그인이 없는데 수정·삭제가 있으면 누구나 남의 글을 지울 수 있으므로, 다음과 같이 정의합니다.

| 항목 | 결정 |
|---|---|
| 글 작성 시 | **작성자명** + **작성자 비밀번호** 입력 (둘 다 필수) |
| 수정 시 | 작성자 비밀번호 확인 |
| 삭제 시 | 작성자 비밀번호 확인 |
| 관리자 | **비밀번호 없이 수정·삭제 가능** |

### 구현상 주의

관리자 우회 경로를 **가장 먼저** 판정해야 합니다. 비밀번호 검사 분기 안에 관리자 확인을 넣으면 관리자가 비회원 글을 지울 수 없게 됩니다. v1.0의 `Post.checkEditable()`이 정확히 이 오류를 갖고 있었고, v2.0에서 수정했습니다(F1). → 3.3절

### 비밀번호 저장 방식

작성자 비밀번호도 **반드시 해시로 저장**합니다. "임시 게시판 비밀번호니까 평문으로"는 안 됩니다. 사용자가 회원 비밀번호와 같은 값을 입력할 가능성이 높기 때문입니다.

## 2.2 임시 비밀번호 발송 실패 시 계정 잠김

```
DB에 임시 비밀번호 저장 → 커밋 → 메일 발송 실패
= 사용자는 새 비밀번호를 모르고, 기존 비밀번호는 이미 사라짐 → 완전 잠김
```

세 가지를 함께 넣어야 합니다.

- **만료 시간** — 임시 비밀번호는 30분 후 무효
- **`must_change_password` 플래그** — 임시 비밀번호로 로그인하면 비밀번호 변경 화면 외 접근 차단
- **재발송 기능** — 메일이 오지 않았을 때의 유일한 복구 경로

## 2.3 미인증 계정이 아이디를 점유함

가입 후 인증 대기 상태의 계정이 아이디와 이메일을 선점합니다. 봇이 인기 아이디를 대량 선점하는 것도 가능합니다.

- 계정 상태를 `PENDING / ACTIVE / DORMANT / WITHDRAWN`으로 관리
- `PENDING`은 24시간 후 만료 (삭제 또는 아이디 해제)
- 인증 토큰은 **해시로 저장**, 1회용, 만료 시간 보유

## 2.4 아이디 중복확인은 방어선이 아님

"중복확인 버튼을 눌렀을 때는 사용 가능"과 "실제 가입 시점" 사이에 다른 사용자가 선점할 수 있습니다(TOCTOU).

- 진짜 방어선은 **`UNIQUE` 제약**
- `DataIntegrityViolationException` → `409 Conflict` 변환 필수 (누락 시 500 에러가 사용자에게 노출됨)
- 중복확인 API는 UX 편의 기능일 뿐임을 팀 내에 명확히 공유

## 2.5 확장자 기반 미디어 판단은 XSS 경로

```
evil.png 업로드 → 실제 내용은 HTML/SVG → <img src="/files/evil.png"> 로 출력
→ 브라우저 MIME 스니핑 → 스크립트 실행
```

**확장자와 클라이언트가 보낸 `Content-Type`은 둘 다 사용자 입력입니다.** 서버가 실제 바이트를 검사해 타입을 판정해야 합니다. → 6.3절

## 2.6 조회수 중복 방지 — **결정됨 (D2), 하이브리드 방식**

### 쿠키만 사용할 때의 한계

| 한계 | 내용 |
|---|---|
| 용량 | 게시물당 쿠키 하나면 도메인당 개수 제한(약 50개)과 총 4KB 제한에 금방 도달 |
| 우회 | 쿠키 삭제 / 시크릿 모드로 무제한 증가 가능 |
| 기기 | 같은 사용자가 PC·모바일에서 각각 카운트됨 |

용량 문제는 **하나의 쿠키에 조회한 게시물 ID를 누적**하고 길이 상한을 두어 해결합니다. 하지만 우회와 기기 문제는 쿠키로 풀 수 없습니다.

### 결정 — 뷰어의 신원으로 방식을 결정한다

| 뷰어 | 추적 방식 | 근거 |
|---|---|---|
| **로그인 사용자** | `post_view_log` 테이블에 `(post_id, member_id, viewed_on)` 기록 | 쿠키 삭제로 우회 불가, 기기 무관, DB 제약이 하루 1회를 원자적으로 보장 |
| **익명 사용자** | 누적 쿠키 (자정 만료) | 식별자가 없으므로 클라이언트 측 추적이 유일한 수단 |

> **원문 요구는 "로그인 인증 후 접근 가능한 게시판"이었으나, 판정 기준을 게시판이 아니라 뷰어로 잡았습니다.**
>
> 이유: 현재 설계에서 Q&A·자료실도 **읽기는 익명에게 열려 있습니다**(5.3절). 게시판 기준으로 판정하면 회원제 게시판을 익명으로 읽는 경우 추적 수단이 없어집니다. 뷰어 기준으로 잡으면 원문 요구를 그대로 만족하면서(회원제 게시판은 대부분 로그인 사용자가 보므로) 익명 조회도 빠짐없이 처리됩니다.
>
> 회원제 게시판을 **읽기까지 로그인 필수**로 만들 경우, 해당 게시판의 조회는 100% DB 기록 경로를 타게 되어 원문 요구와 정확히 일치합니다. → 10장 미결정 #1

### 핵심 이점 — 복합 기본키가 곧 규칙

`post_view_log`의 `PRIMARY KEY (post_id, member_id, viewed_on)`가 "하루에 한 번"을 DB 레벨에서 강제합니다.

```sql
INSERT INTO`` post_view_log (post_id, member_id, viewed_on)
VALUES (?, ?, ?) ON CONFLICT DO NOTHING
```

영향 행 수가 `1`이면 오늘 첫 조회, `0`이면 이미 조회함. **조회-후-삽입(check-then-act)이 아니므로 동시 요청에서도 경합이 없습니다.** → 6.2절

## 2.7 좋아요에 중복 방지가 없음

"누르면 1 증가"만 있으면 무한 클릭이 가능합니다. 회원제이므로 `(post_id, member_id)` UNIQUE 테이블이 정답이고, 이렇게 만들면 이후 "취소 기능", "내가 좋아요한 글" 요구가 와도 스키마를 뒤집지 않습니다.

## 2.8 세 게시판의 모델링 — **결정됨 (D3)**

| | 자유 | Q&A | 자료실 |
|---|---|---|---|
| CRUD, 페이징, 조회수, 좋아요 | ● | ● | ● |
| 쓰기에 로그인 필요 | ✗ | ● | ● |
| 댓글 | ✗ | ● | ✗ |
| 첨부파일 | ✗ | ✗ | ● |

**차이가 전부 "정책"입니다.**

> 게시판 종류는 타입이 아니라 정책의 집합이다.
> → `Post`는 하나의 엔티티, `Board`가 정책을 데이터로 보유한다.

이렇게 하면 "공지사항 게시판 추가"가 **코드 변경 없이 `board` 테이블에 행 하나 추가**로 끝납니다. 반대로 `FreePost / QnaPost / ArchivePost` 세 엔티티로 나누면 페이징·조회수·좋아요 로직이 세 벌 복제됩니다.

### v2.0 변경 — 읽기 권한과 쓰기 권한의 분리

`requires_auth` 하나로는 "익명도 읽을 수 있지만 쓰려면 로그인"과 "읽기부터 로그인 필수"를 구분할 수 없습니다. 2.6절의 조회수 판정과도 직접 연결되므로 두 컬럼으로 분리합니다.

```
requires_auth  →  requires_auth_to_read
                  requires_auth_to_write
```

이렇게 두면 10장 미결정 #1이 어느 쪽으로 결정되든 **스키마 변경 없이 시드 데이터만 고치면** 됩니다.

## 2.9 검토 요약

| # | 항목 | 성격 | 상태 | 조치 |
|---|---|---|---|---|
| 2.1 | 비회원 글 수정·삭제 권한 | 명세 누락 | **결정 (D1)** | 작성자 비밀번호 + 관리자 우회 |
| 2.2 | 임시 비밀번호 발송 실패 | 장애 시나리오 | 대기 | 만료 + 강제변경 + 재발송 |
| 2.3 | 미인증 계정 점유 | 명세 누락 | 대기 | 계정 상태 + 만료 정책 |
| 2.4 | 아이디 중복확인 | 오해 소지 | 확정 | UNIQUE + 409 처리 |
| 2.5 | 확장자 기반 판단 | **보안** | **결정 (D4)** | 실제 MIME 검증 |
| 2.6 | 조회수 중복 방지 | 기술 제약 | **결정 (D2)** | 하이브리드 추적 |
| 2.7 | 좋아요 중복 | 명세 누락 | 확정 | UNIQUE 테이블 |
| 2.8 | 게시판 모델링 | 설계 | **결정 (D3)** | Board 정책 데이터화 + 읽기/쓰기 분리 |

---

# 3. 도메인 모델

## 3.1 엔티티 관계

```
Member ──< Post >── Board
   │        │
   │        ├──< Comment
   │        ├──< Attachment
   │        ├──< PostLike    >── Member
   │        └──< PostViewLog >── Member
   │
   └──< VerificationToken
```

## 3.2 Board — 정책의 보유자

```java
package page.sanotehu.board.backend.board.domain;

@Entity
public class Board {

    @Id @GeneratedValue
    private Long id;

    private String slug;                    // free, qna, archive
    private String name;

    private boolean requiresAuthToRead;     // 읽기에 로그인 필요?
    private boolean requiresAuthToWrite;    // 쓰기에 로그인 필요?
    private boolean allowsComment;
    private boolean allowsAttachment;
    private int displayOrder;

    public void checkReadable(Optional<Member> viewer) {
        if (requiresAuthToRead && viewer.isEmpty()) {
            throw new AuthenticationRequiredException(slug);
        }
    }

    public void checkWritable(Optional<Member> writer) {
        if (requiresAuthToWrite && writer.isEmpty()) {
            throw new AuthenticationRequiredException(slug);
        }
    }

    public void checkCommentAllowed() {
        if (!allowsComment) throw new CommentNotAllowedException(slug);
    }

    public void checkAttachmentAllowed() {
        if (!allowsAttachment) throw new AttachmentNotAllowedException(slug);
    }

    /** 비회원 글을 받을 수 있는 게시판인가 — 작성자 비밀번호 요구 여부와 동일 */
    public boolean acceptsGuestPost() {
        return !requiresAuthToWrite;
    }
}
```

초기 시드 데이터:

| slug | name | read | write | comment | attachment |
|---|---|---|---|---|---|
| `free` | 자유게시판 | false | false | false | false |
| `qna` | Q&A 게시판 | false | true | true | false |
| `archive` | 자료실 | false | true | false | true |

> `requires_auth_to_read`를 전부 `false`로 시드했습니다. 10장 미결정 #1이 "읽기도 로그인 필수"로 결정되면 `qna`·`archive`의 값만 `true`로 바꾸는 마이그레이션 한 줄이면 됩니다.

## 3.3 Post — 회원 글과 비회원 글의 배타 관계

```java
package page.sanotehu.board.backend.post.domain;

@Entity
public class Post {

    @Id @GeneratedValue private Long id;

    @ManyToOne(fetch = LAZY) private Board board;
    @ManyToOne(fetch = LAZY) private Member member;      // 비회원 글은 null

    private String guestNickname;        // 비회원 전용 — 작성자명
    private String guestPasswordHash;    // 비회원 전용 — 해시로만 보관

    private String title;
    private String content;
    private int viewCount;
    private int likeCount;
    private LocalDateTime deletedAt;

    /**
     * 수정·삭제 권한 판정 (D1)
     *
     * 판정 순서가 중요하다.
     *   1) 관리자      → 무조건 통과 (비회원 글이어도 비밀번호 불필요)
     *   2) 회원 글     → 작성자 본인만
     *   3) 비회원 글   → 작성자 비밀번호 일치
     *
     * PasswordEncoder를 필드로 두지 않고 파라미터로 받는다.
     * 엔티티가 스프링 빈에 의존하면 JPA가 생성하는 인스턴스에서 null이 되고,
     * 단위 테스트에서도 컨텍스트가 필요해진다.
     */
    public void checkEditable(Optional<Member> actor,
                              String rawGuestPassword,
                              PasswordEncoder encoder) {

        // 1) 관리자 우회 — 반드시 최상단
        if (actor.map(Member::isAdmin).orElse(false)) {
            return;
        }

        // 2) 회원 글
        if (member != null) {
            boolean owner = actor.map(a -> a.getId().equals(member.getId())).orElse(false);
            if (!owner) throw new AccessDeniedException("not the author");
            return;
        }

        // 3) 비회원 글
        if (rawGuestPassword == null
                || !encoder.matches(rawGuestPassword, guestPasswordHash)) {
            throw new AccessDeniedException("guest password mismatch");
        }
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isGuestPost() {
        return member == null;
    }
}
```

권한 판정을 컨트롤러가 아니라 **엔티티가 소유**합니다. 새로운 진입점(관리자 API, 배치)이 생겨도 규칙이 한 곳에만 존재합니다.

### v1.0에서 고친 두 가지

| 문제 | v1.0 | v2.0 |
|---|---|---|
| **F1** — 관리자가 비회원 글을 삭제할 수 없음 | 관리자 확인이 회원 글 분기 안에만 있었음 | 관리자 우회를 메서드 최상단으로 이동 |
| **F2** — 엔티티가 `encoder`를 참조하지만 주입 경로 없음 | 필드 참조 (컴파일 불가) | 메서드 파라미터로 전달 |

---

# 4. 데이터베이스 스키마

Flyway 마이그레이션으로 관리합니다. `spring.jpa.hibernate.ddl-auto`는 `validate`로 고정하고, 스키마 변경은 반드시 마이그레이션 파일로만 수행합니다.

## 4.1 DDL

```sql
-- V1__init.sql

-- ─────────────────────────────────────────────
-- 회원
-- ─────────────────────────────────────────────
CREATE TABLE member (
    id                   BIGSERIAL PRIMARY KEY,
    username             VARCHAR(30)  NOT NULL UNIQUE,
    password_hash        VARCHAR(100) NOT NULL,
    name                 VARCHAR(50)  NOT NULL,
    email                VARCHAR(255) NOT NULL UNIQUE,
    phone                VARCHAR(20)  NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    role                 VARCHAR(20)  NOT NULL DEFAULT 'USER',
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    temp_password_expires_at TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 이메일 인증 / 비밀번호 재설정 공용 토큰
CREATE TABLE verification_token (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT      NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,   -- 원문은 저장하지 않는다
    purpose    VARCHAR(30) NOT NULL,          -- EMAIL_VERIFY | PASSWORD_RESET
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ
);
CREATE INDEX idx_token_member ON verification_token (member_id, purpose);

-- ─────────────────────────────────────────────
-- 게시판 정책
-- ─────────────────────────────────────────────
CREATE TABLE board (
    id                      BIGSERIAL PRIMARY KEY,
    slug                    VARCHAR(30) NOT NULL UNIQUE,
    name                    VARCHAR(50) NOT NULL,
    requires_auth_to_read   BOOLEAN NOT NULL DEFAULT FALSE,
    requires_auth_to_write  BOOLEAN NOT NULL,
    allows_comment          BOOLEAN NOT NULL,
    allows_attachment       BOOLEAN NOT NULL,
    display_order           INT     NOT NULL DEFAULT 0
);

-- ─────────────────────────────────────────────
-- 게시글
-- ─────────────────────────────────────────────
CREATE TABLE post (
    id                  BIGSERIAL PRIMARY KEY,
    board_id            BIGINT NOT NULL REFERENCES board(id),
    member_id           BIGINT     REFERENCES member(id),  -- 비회원 글은 NULL
    guest_nickname      VARCHAR(30),
    guest_password_hash VARCHAR(100),
    title               VARCHAR(200) NOT NULL,
    content             TEXT         NOT NULL,
    view_count          INT NOT NULL DEFAULT 0,
    like_count          INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    -- 회원 글이면 guest_* 전부 NULL, 비회원 글이면 guest_* 전부 NOT NULL
    CONSTRAINT post_author_ck CHECK (
        (member_id IS NOT NULL
             AND guest_nickname IS NULL AND guest_password_hash IS NULL)
        OR
        (member_id IS NULL
             AND guest_nickname IS NOT NULL AND guest_password_hash IS NOT NULL)
    )
);
CREATE INDEX idx_post_list ON post (board_id, deleted_at, id DESC);

-- ─────────────────────────────────────────────
-- 댓글
-- ─────────────────────────────────────────────
CREATE TABLE comment (
    id         BIGSERIAL PRIMARY KEY,
    post_id    BIGINT NOT NULL REFERENCES post(id),
    member_id  BIGINT NOT NULL REFERENCES member(id),
    content    TEXT   NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_comment_post ON comment (post_id, deleted_at, id);

-- ─────────────────────────────────────────────
-- 첨부파일
-- ─────────────────────────────────────────────
CREATE TABLE attachment (
    id            BIGSERIAL PRIMARY KEY,
    post_id       BIGINT NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    original_name VARCHAR(255) NOT NULL,
    stored_name   VARCHAR(100) NOT NULL UNIQUE,  -- UUID 기반
    content_type  VARCHAR(100) NOT NULL,         -- 서버가 실제로 감지한 값
    media_kind    VARCHAR(10)  NOT NULL,         -- IMAGE | VIDEO | AUDIO | FILE
    byte_size     BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_attachment_post ON attachment (post_id);

-- ─────────────────────────────────────────────
-- 좋아요
-- ─────────────────────────────────────────────
CREATE TABLE post_like (
    post_id    BIGINT NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    member_id  BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, member_id)
);

-- ─────────────────────────────────────────────
-- 조회 기록 (D2) — 로그인 사용자 전용
-- 복합 PK가 "하루에 한 번" 규칙 그 자체다.
-- ─────────────────────────────────────────────
CREATE TABLE post_view_log (
    post_id    BIGINT NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    member_id  BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    viewed_on  DATE   NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, member_id, viewed_on)
);
-- 보관 주기 정리 배치용
CREATE INDEX idx_view_log_date ON post_view_log (viewed_on);
```

```sql
-- V2__seed_board.sql
INSERT INTO board (slug, name, requires_auth_to_read, requires_auth_to_write,
                   allows_comment, allows_attachment, display_order)
VALUES
  ('free',    '자유게시판', false, false, false, false, 1),
  ('qna',     'Q&A 게시판', false, true,  true,  false, 2),
  ('archive', '자료실',     false, true,  false, true,  3);
```

## 4.2 설계 근거

| 결정 | 근거 |
|---|---|
| `post_author_ck` 체크 제약 | 회원 글과 비회원 글의 배타 관계를 DB가 강제. v2.0에서 `guest_nickname`까지 포함하도록 확장 — D1에 따라 작성자명이 필수가 됐기 때문 |
| `deleted_at` 소프트 삭제 | 댓글이 달린 글을 물리 삭제하면 댓글이 고아가 됨. "삭제된 게시물입니다" 표시도 가능 |
| `like_count` 반정규화 | 매번 `COUNT(*)`를 돌리지 않기 위한 캐시. **`post_like`가 진실 원천이고 `like_count`는 파생값**임을 코드 주석에 명시 |
| `post_view_log` 복합 PK | "하루에 한 번"을 애플리케이션이 아니라 DB가 강제. `ON CONFLICT DO NOTHING`의 영향 행 수가 곧 판정 결과 |
| `viewed_on`을 `DATE`로 | 타임스탬프로 두면 "같은 날"을 계산해야 함. 날짜로 잘라 저장하면 비교가 곧 동등 검사 |
| `token_hash` 저장 | DB가 유출돼도 토큰을 재사용할 수 없음 |
| `requires_auth_to_read` 분리 | 조회수 판정(2.6)과 접근 제어가 같은 컬럼에 묶이지 않게 함. 미결정 #1이 어느 쪽이든 시드만 변경 |
| `idx_post_list` 복합 인덱스 | 목록 조회의 `WHERE board_id = ? AND deleted_at IS NULL ORDER BY id DESC`를 그대로 커버 |
| `TIMESTAMPTZ` | 타임존 문제를 DB 레벨에서 제거 |

## 4.3 `post_view_log` 증가 정책

조회 기록은 무한히 쌓입니다. 운영 시 정리 정책이 필요합니다.

```sql
-- 예: 90일 이전 기록 삭제 (조회수 자체는 post.view_count에 누적돼 있으므로 안전)
DELETE FROM post_view_log WHERE viewed_on < CURRENT_DATE - INTERVAL '90 days';
```

`post.view_count`가 누적 총계를 이미 갖고 있으므로, 로그를 지워도 조회수는 보존됩니다. 로그는 **중복 판정용 임시 데이터**일 뿐입니다.

---

# 5. 백엔드 구성

## 5.1 의존성

Spring Boot 4.0에서 **여러 스타터 이름이 변경**되었습니다. 기존 이름도 동작하지만 deprecated이며 이후 제거 예정입니다.

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'    // 구 -web
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-mail'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'  // 메일 템플릿
    implementation 'org.springframework.boot:spring-boot-starter-flyway'     // 4.0부터 명시 필요
    implementation 'org.apache.tika:tika-core:3.+'                           // 실제 MIME 감지

    runtimeOnly 'org.postgresql:postgresql'
}
```

> 정확한 아티팩트 좌표는 start.spring.io에서 Spring Boot 4.1.1로 프로젝트를 한 번 생성해 대조하십시오.

### Spring Boot 4.x에서 함께 알아둘 변경점

| 변경 | 영향 |
|---|---|
| **Jackson 3** — 패키지가 `tools.jackson.*` | 커스텀 Serializer의 import 전면 변경. `@JsonComponent` → `@JacksonComponent` |
| `@SpringBootTest`가 MockMvc를 자동 제공하지 않음 | `@AutoConfigureMockMvc` 명시 필요 |
| `@MockBean` / `@SpyBean` 제거 | `@MockitoBean` / `@MockitoSpyBean`으로 대체 |
| 모듈화 | 패키지가 `org.springframework.boot.<module>` 기준으로 재편 |
| 기술별 테스트 스타터 신설 | `spring-boot-starter-<기술>-test` 패턴 |

### Spring Security 7 변경점

| 변경 | 영향 |
|---|---|
| **람다 DSL 필수** | `.and()` 체이닝 제거 — 사용 시 컴파일 불가 |
| `AntPathRequestMatcher`, `MvcRequestMatcher` 제거 | `PathPatternRequestMatcher` 사용 |
| `AccessDecisionManager` 제거 | `AuthorizationManager`로 대체 |
| Jackson 3 지원 | `SecurityJackson2Modules` → `SecurityJacksonModules` |

## 5.2 패키지 구조

모든 기능에 포트/어댑터를 두면 학습 부담만 커집니다. **외부 시스템에 닿는 곳에만** 적용합니다. 이 프로젝트에서 그건 정확히 **메일 발송**과 **파일 저장** 둘입니다.

```
page.sanotehu.board.backend
├── BoardApplication.java
├── common/                    ApiError, PageResponse, BaseTimeEntity, GlobalExceptionHandler
├── config/                    SecurityConfig, WebConfig, AsyncConfig, JpaAuditingConfig,
│                              SpaResourceConfig
├── member/
│   ├── domain/                Member, MemberStatus, MemberRole, VerificationToken
│   ├── application/
│   │   ├── SignupService, AuthService, PasswordResetService, MemberService
│   │   └── port/out/          SendMailPort                    ← 포트
│   └── adapter/
│       ├── in/web/            MemberController, AuthController, dto/
│       └── out/mail/          JavaMailAdapter                 ← 어댑터
├── board/
│   ├── domain/                Board
│   ├── application/           BoardService
│   └── adapter/in/web/        BoardController
├── post/
│   ├── domain/                Post, PostLike, PostViewLog
│   ├── application/
│   │   ├── PostService, PostLikeService
│   │   └── view/              PostViewCountService            ← 조회수 판정 진입점
│   │       ├── PostViewTracker              (인터페이스)
│   │       ├── MemberPostViewTracker        (로그인 — DB 기록)
│   │       └── AnonymousPostViewTracker     (익명 — 쿠키)
│   └── adapter/in/web/        PostController, dto/
├── comment/
│   ├── domain/                Comment
│   ├── application/           CommentService
│   └── adapter/in/web/        CommentController
└── attachment/
    ├── domain/                Attachment, MediaKind
    ├── application/
    │   ├── AttachmentService, AttachmentUploader
    │   └── port/out/          StoreFilePort                   ← 포트
    └── adapter/
        ├── in/web/            AttachmentController
        └── out/storage/       LocalFileStorageAdapter         ← 어댑터
```

### 포트 정의

```java
package page.sanotehu.board.backend.member.application.port.out;

public interface SendMailPort {
    void send(String to, String subject, String htmlBody);
}
```

```java
package page.sanotehu.board.backend.attachment.application.port.out;

public interface StoreFilePort {
    StoredFile store(InputStream content, String originalName);
    Resource load(String storedName);
    void delete(String storedName);
}
```

**이 두 포트의 실익**

- 로컬 디스크 → S3, SMTP → SES 전환 시 도메인 코드를 건드리지 않음
- 테스트에서 실제 메일이 발송되거나 실제 파일이 쌓이지 않음

반대로 `post` 패키지에 `PostRepositoryPort`를 만드는 것은 대부분 오버헤드입니다. JPA Repository를 교체할 일이 실제로 발생하지 않기 때문입니다. **포트는 교체 가능성이 실재하는 곳에만 둡니다.**

### `PostViewTracker`는 포트인가

아닙니다. 외부 시스템 교체를 위한 포트가 아니라 **런타임에 뷰어 종류로 분기하는 전략(Strategy)** 입니다. 이름을 `...Port`로 짓지 마십시오. 포트와 전략을 같은 접미사로 부르면 "포트는 교체 가능성이 실재하는 곳에만"이라는 규칙이 흐려집니다.

## 5.3 SecurityConfig

핵심 판단: **게시판별 인증 정책을 URL 패턴으로 표현하지 않습니다.**

```
Security 필터의 책임 : "인증된 사용자인가?" 까지
도메인의 책임        : "이 게시판을 읽을 / 쓸 수 있는가?" (Board.checkReadable / checkWritable)
```

URL로 전부 표현하면 게시판이 늘어날 때마다 `SecurityConfig`를 수정해야 합니다. 정책을 데이터로 뒀는데 인가만 하드코딩하면 3장의 설계가 무너집니다.

```java
package page.sanotehu.board.backend.config;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // 읽기는 필터에서 막지 않는다 — Board.checkReadable()이 판정
                .requestMatchers(HttpMethod.GET, "/api/boards/**", "/api/posts/**",
                                 "/api/files/**").permitAll()
                .requestMatchers("/api/auth/**",
                                 "/api/members/signup",
                                 "/api/members/username-availability",
                                 "/api/members/verify-email",
                                 "/api/members/password-reset").permitAll()
                // 쓰기도 익명 통과 — Board.checkWritable()이 판정
                .requestMatchers(HttpMethod.POST,  "/api/boards/*/posts").permitAll()
                .requestMatchers(HttpMethod.PUT,    "/api/posts/*").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/posts/*").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler(new JsonAuthenticationSuccessHandler())
                .failureHandler(new JsonAuthenticationFailureHandler())
            )
            .logout(out -> out
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((req, res, a) -> res.setStatus(204))
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> res.sendError(401))
                .accessDeniedHandler((req, res, e) -> res.sendError(403))
            )
            .sessionManagement(s -> s
                .sessionFixation(SessionFixationConfigurer::changeSessionId)
                .maximumSessions(1)
            );
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
```

> **PUT/DELETE를 `permitAll`로 두는 것이 위험해 보인다면** — 필터를 통과해도 `Post.checkEditable()`이 소유자 또는 작성자 비밀번호를 반드시 검증합니다. 인가 판정을 두 곳에 나눠 두면 한쪽만 고쳤을 때 구멍이 생기므로, **한 곳(도메인)에 모읍니다.**

### 세션 쿠키 + CSRF를 택한 이유

Vite 프록시로 same-origin을 유지하므로 `HttpOnly` 세션 쿠키가 자연스럽게 동작합니다.

| | 세션 쿠키 | JWT (localStorage) |
|---|---|---|
| XSS 노출 | `HttpOnly`로 JS 접근 차단 | 토큰 탈취 가능 |
| 로그아웃 | 서버에서 즉시 무효 | 만료 전까지 유효 |
| CSRF | 방어 필요 (토큰) | 자동 전송 안 되므로 불필요 |
| 개발/운영 동일성 | 프록시로 동일 | 동일 |

CSRF 흐름: 서버가 `XSRF-TOKEN` 쿠키를 내려주고, 프론트가 이를 읽어 `X-XSRF-TOKEN` 헤더로 되돌려 보냅니다.

### `must_change_password` 처리

필터로 막지 않고 `GET /api/me` 응답에 플래그를 실어 프론트에서 라우트 가드로 처리합니다. 서버 쪽은 비밀번호 변경 외 쓰기 API에서 한 번 더 검증합니다.

## 5.4 예외 처리

```java
package page.sanotehu.board.backend.common;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> onConflict(DataIntegrityViolationException e) {
        return ResponseEntity.status(409)
            .body(ApiError.of("DUPLICATE", "이미 사용 중인 값입니다."));
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    ResponseEntity<ApiError> onAuthRequired(AuthenticationRequiredException e) {
        return ResponseEntity.status(401)
            .body(ApiError.of("AUTH_REQUIRED", "로그인이 필요한 게시판입니다."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> onDenied(AccessDeniedException e) {
        return ResponseEntity.status(403)
            .body(ApiError.of("FORBIDDEN", "권한이 없거나 비밀번호가 일치하지 않습니다."));
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    ResponseEntity<ApiError> onBadFile(UnsupportedFileTypeException e) {
        return ResponseEntity.status(415)
            .body(ApiError.of("UNSUPPORTED_FILE", "허용되지 않는 파일 형식입니다."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException e) { /* ... */ }
}
```

> **403 메시지에 주의** — "비밀번호가 일치하지 않습니다"와 "권한이 없습니다"를 분리해 응답하면, 비회원 글에 대한 무차별 대입 시 유효한 힌트를 줍니다. 하나의 메시지로 합칩니다.

## 5.5 API 엔드포인트

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| `POST` | `/api/members/signup` | — | 회원가입 (이메일 인증 메일 발송) |
| `GET` | `/api/members/username-availability?username=` | — | 아이디 중복확인 (UX용) |
| `GET` | `/api/members/verify-email?token=` | — | 이메일 인증 완료 |
| `POST` | `/api/members/verify-email/resend` | — | 인증 메일 재발송 |
| `POST` | `/api/members/password-reset` | — | 임시 비밀번호 발송 |
| `POST` | `/api/auth/login` | — | 로그인 |
| `POST` | `/api/auth/logout` | 필요 | 로그아웃 |
| `GET` | `/api/me` | 필요 | 현재 사용자 (`mustChangePassword` 포함) |
| `PATCH` | `/api/me` | 필요 | 회원정보 수정 |
| `PATCH` | `/api/me/password` | 필요 | 비밀번호 변경 |
| `GET` | `/api/boards` | — | 게시판 목록 (정책 포함) |
| `GET` | `/api/boards/{slug}/posts?page=&size=&q=` | 정책 | 게시글 목록 |
| `POST` | `/api/boards/{slug}/posts` | 정책 | 글 작성 (비회원은 `guestNickname`, `guestPassword` 포함) |
| `GET` | `/api/posts/{id}` | 정책 | 글 읽기 (조회수 판정 수행) |
| `PUT` | `/api/posts/{id}` | 정책 | 글 수정 (비회원은 `guestPassword` 포함) |
| `DELETE` | `/api/posts/{id}` | 정책 | 글 삭제 (비회원은 `guestPassword` 포함) |
| `POST` | `/api/posts/{id}/like` | 필요 | 좋아요 |
| `GET` | `/api/posts/{id}/comments` | 정책 | 댓글 목록 |
| `POST` | `/api/posts/{id}/comments` | 필요 | 댓글 작성 |
| `PUT` | `/api/comments/{id}` | 필요 | 댓글 수정 |
| `DELETE` | `/api/comments/{id}` | 필요 | 댓글 삭제 |
| `POST` | `/api/posts/{id}/attachments` | 필요 | 첨부 업로드 |
| `GET` | `/api/files/{attachmentId}` | 정책 | 인라인 조회 (`img`/`video`/`audio`) |
| `GET` | `/api/files/{attachmentId}?download=true` | 정책 | 다운로드 |

> **"정책"** = Security 필터는 통과시키고 `Board`/`Post`의 도메인 규칙이 판정합니다.
>
> **비회원 비밀번호는 URL 쿼리가 아니라 요청 본문에 담습니다.** `DELETE`도 본문을 허용하지만, 프록시·로그에 남는 것을 확실히 피하려면 `POST /api/posts/{id}/delete` 형태를 쓰는 것도 방법입니다. → 10장 미결정 #5

---

# 6. 핵심 기능 구현 지침

## 6.1 이메일 발송 — 트랜잭션 경계가 전부

```java
@Service
@RequiredArgsConstructor
public class SignupService {

    private final MemberRepository memberRepository;
    private final VerificationTokenRepository tokenRepository;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;

    @Transactional
    public Long signup(SignupCommand cmd) {
        Member member = Member.pending(
            cmd.username(), encoder.encode(cmd.password()),
            cmd.name(), cmd.email(), cmd.phone()
        );
        memberRepository.save(member);   // UNIQUE 위반 → 409로 변환

        String raw = TokenGenerator.generate();
        tokenRepository.save(VerificationToken.emailVerify(
            member, sha256(raw), Duration.ofHours(24)));

        events.publishEvent(new SignupCompleted(member.getEmail(), raw));
        return member.getId();
    }
}

@Component
@RequiredArgsConstructor
class SignupMailListener {

    private final SendMailPort sendMailPort;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(SignupCompleted e) {
        sendMailPort.send(e.email(), "이메일 인증", renderVerifyMail(e.rawToken()));
    }
}
```

### 왜 `AFTER_COMMIT`인가

| 순서 | 결과 |
|---|---|
| 트랜잭션 **안**에서 메일 발송 | 이후 롤백돼도 **메일은 이미 나감** — 되돌릴 방법 없음 |
| **커밋 후** 메일 발송 | 메일 실패 시 계정은 남고 재발송으로 복구 가능 |

> **일반 원칙** — 되돌릴 수 없는 부수효과(메일, 외부 API 호출, 결제)는 항상 커밋 이후에 실행합니다.

### 비밀번호 찾기 — 계정 열거 방지

```java
@Transactional
public void issueTemporaryPassword(String email) {
    memberRepository.findByEmail(email).ifPresent(m -> {
        String temp = TemporaryPassword.generate();   // 12자 이상, SecureRandom
        m.assignTemporaryPassword(encoder.encode(temp), Duration.ofMinutes(30));
        events.publishEvent(new TemporaryPasswordIssued(m.getEmail(), temp));
    });
    // 존재 여부와 무관하게 항상 같은 응답
}
```

이메일이 존재하지 않아도 `200 OK`와 "이메일을 보냈습니다"를 반환합니다. `404`를 내면 공격자가 가입된 이메일 목록을 수집할 수 있습니다.

## 6.2 조회수 중복 방지 — 하이브리드 추적 (D2)

### 전체 흐름

```
GET /api/posts/{id}
        │
        ▼
  PostViewCountService.recordView(postId, viewer, req, res)
        │
        ├─ viewer 가 로그인 사용자 ──▶ MemberPostViewTracker
        │                              INSERT post_view_log ON CONFLICT DO NOTHING
        │                              영향 행 1 → 첫 조회
        │
        └─ viewer 가 익명        ──▶ AnonymousPostViewTracker
                                       누적 쿠키에 [postId] 포함 여부 검사
                                       미포함 → 첫 조회, 쿠키에 추가
        │
        ▼
  첫 조회이면 UPDATE post SET view_count = view_count + 1  (REQUIRES_NEW)
```

### 전략 인터페이스

```java
package page.sanotehu.board.backend.post.application.view;

public interface PostViewTracker {
    /** 오늘 이 게시물을 처음 보는 것이면 true (그리고 본 것으로 기록한다) */
    boolean markViewedIfFirstToday(long postId, ViewerContext viewer);
}
```

```java
public record ViewerContext(
    Long memberId,                    // 익명이면 null
    HttpServletRequest request,
    HttpServletResponse response
) {
    public boolean isAuthenticated() { return memberId != null; }
}
```

### 로그인 사용자 — DB 기록

```java
@Component
@RequiredArgsConstructor
public class MemberPostViewTracker implements PostViewTracker {

    private final PostViewLogRepository repository;
    private final Clock clock;

    @Override
    public boolean markViewedIfFirstToday(long postId, ViewerContext viewer) {
        LocalDate today = LocalDate.now(clock);   // Asia/Seoul 고정 Clock 빈
        return repository.tryRecord(postId, viewer.memberId(), today) == 1;
    }
}
```

```java
public interface PostViewLogRepository extends JpaRepository<PostViewLog, PostViewLogId> {

    /**
     * 복합 PK가 "하루 1회"를 강제한다.
     * 삽입되면 1, 이미 존재하면 0을 반환 — 조회 후 삽입(check-then-act)이 아니므로
     * 동시 요청에서도 경합이 발생하지 않는다.
     */
    @Modifying
    @Query(value = """
        INSERT INTO post_view_log (post_id, member_id, viewed_on)
        VALUES (:postId, :memberId, :viewedOn)
        ON CONFLICT DO NOTHING
        """, nativeQuery = true)
    int tryRecord(@Param("postId") Long postId,
                  @Param("memberId") Long memberId,
                  @Param("viewedOn") LocalDate viewedOn);
}
```

**이 방식의 장점**

| 항목 | 쿠키 방식 | DB 기록 방식 |
|---|---|---|
| 쿠키 삭제로 우회 | 가능 | 불가능 |
| 여러 기기 | 기기마다 별도 카운트 | 사용자 단위로 1회 |
| 동시 요청 경합 | 마지막 응답이 이김 | DB 제약이 원자적으로 차단 |
| 저장 비용 | 없음 | 행 누적 (4.3절 정리 정책 필요) |

### 익명 사용자 — 누적 쿠키

```java
@Component
public class AnonymousPostViewTracker implements PostViewTracker {

    private static final String NAME = "viewedPosts";
    private static final int MAX_LENGTH = 3000;   // 4KB 한계 대비 여유

    @Override
    public boolean markViewedIfFirstToday(long postId, ViewerContext viewer) {
        HttpServletRequest req = viewer.request();
        HttpServletResponse res = viewer.response();

        String current = readCookie(req, NAME).orElse("");
        String token = "[" + postId + "]";
        if (current.contains(token)) return false;

        String next = current + token;
        if (next.length() > MAX_LENGTH) {           // 오래된 항목부터 버림
            next = next.substring(next.length() - MAX_LENGTH);
        }
        res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NAME, next)
            .path("/")
            .httpOnly(true)
            .sameSite("Lax")
            .maxAge(secondsUntilMidnight())         // "하루에 한 번" = 자정 만료
            .build().toString());
        return true;
    }

    private long secondsUntilMidnight() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        return Duration.between(now,
            now.toLocalDate().plusDays(1).atStartOfDay(now.getZone())).getSeconds();
    }
}
```

### 진입점 — 전략 선택

```java
@Service
@RequiredArgsConstructor
public class PostViewCountService {

    private final MemberPostViewTracker memberTracker;
    private final AnonymousPostViewTracker anonymousTracker;
    private final PostRepository postRepository;

    public void recordView(long postId, ViewerContext viewer) {
        PostViewTracker tracker = viewer.isAuthenticated()
            ? memberTracker
            : anonymousTracker;

        if (tracker.markViewedIfFirstToday(postId, viewer)) {
            increase(postId);
        }
    }

    /** 조회수 증가 실패가 글 읽기를 막아서는 안 된다 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void increase(long postId) {
        postRepository.increaseViewCount(postId);
    }
}
```

### 증가는 반드시 원자적 UPDATE

```java
@Modifying(clearAutomatically = true)
@Query("update Post p set p.viewCount = p.viewCount + 1 where p.id = :id")
void increaseViewCount(@Param("id") Long id);
```

엔티티를 조회해 `post.setViewCount(post.getViewCount() + 1)`로 처리하면 동시 요청에서 **갱신 손실(lost update)** 이 발생합니다.

### `Clock` 빈을 쓰는 이유

```java
@Bean
Clock clock() {
    return Clock.system(ZoneId.of("Asia/Seoul"));
}
```

`LocalDate.now()`를 직접 부르면 "자정 직전 조회 → 자정 직후 재조회" 같은 경계 케이스를 테스트할 수 없습니다. `Clock`을 주입하면 테스트에서 `Clock.fixed(...)`로 시각을 고정할 수 있습니다.

## 6.3 첨부파일 — 명세의 안전한 재해석 (D4)

```java
@Service
@RequiredArgsConstructor
public class AttachmentUploader {

    private static final Set<String> ALLOWED = Set.of(
        "image/png", "image/jpeg", "image/gif", "image/webp",
        "video/mp4", "video/webm",
        "audio/mpeg", "audio/ogg",
        "application/pdf", "application/zip",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final StoreFilePort storeFilePort;
    private final Tika tika;

    public Attachment upload(Post post, MultipartFile file) throws IOException {
        // 1. 확장자·클라이언트 Content-Type을 무시하고 실제 바이트로 판정
        String detected = tika.detect(file.getInputStream(), file.getOriginalFilename());
        if (!ALLOWED.contains(detected)) {
            throw new UnsupportedFileTypeException(detected);
        }

        // 2. 저장명은 UUID — 원본명은 DB에만
        StoredFile stored = storeFilePort.store(
            file.getInputStream(), file.getOriginalFilename());

        return Attachment.of(post,
            sanitize(file.getOriginalFilename()),
            stored.storedName(),
            detected,
            MediaKind.from(detected),   // 서버가 판정해 DB에 저장
            file.getSize());
    }
}
```

```java
public enum MediaKind {
    IMAGE, VIDEO, AUDIO, FILE;

    public static MediaKind from(String contentType) {
        if ("image/svg+xml".equals(contentType)) return FILE;   // SVG는 스크립트 실행 가능
        if (contentType.startsWith("image/")) return IMAGE;
        if (contentType.startsWith("video/")) return VIDEO;
        if (contentType.startsWith("audio/")) return AUDIO;
        return FILE;
    }
}
```

**명세의 "확장자에 따라"를 "서버가 판정한 `media_kind`에 따라"로 대체합니다.** 프론트는 확장자를 파싱하지 않고 API가 내려준 `mediaKind`만 보고 태그를 고릅니다.

### 응답 헤더

```java
// 다운로드
.header(HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment()
            .filename(originalName, StandardCharsets.UTF_8)
            .build().toString())
.header("X-Content-Type-Options", "nosniff")
```

- `nosniff`가 없으면 브라우저가 내용을 보고 타입을 추측해 HTML로 실행할 수 있습니다.
- **한글 파일명**은 `ContentDisposition` 빌더에 UTF-8을 명시해야 깨지지 않습니다.

### 업로드 보안 체크리스트 — **구현 필수 항목** (D4)

이 8개 항목은 권고가 아니라 **완료 조건**입니다. 자료실 기능의 코드 리뷰 시 항목별로 확인하십시오.

| # | 항목 | 확인 방법 |
|---|---|---|
| 1 | 실제 바이트로 MIME 감지 (확장자·클라이언트 헤더 불신) | `.jpg`로 이름만 바꾼 HTML 파일 업로드 → 415 |
| 2 | 화이트리스트 검증 (블랙리스트 금지) | `ALLOWED` 집합 외 전부 거부 |
| 3 | 저장 파일명 UUID화 | `../../etc/passwd` 이름 업로드 → 저장 경로 변화 없음 |
| 4 | 원본 파일명 sanitize 후 DB에만 저장 | 경로 구분자·제어문자 제거 확인 |
| 5 | SVG를 IMAGE에서 제외 | SVG 업로드 → `media_kind = FILE` |
| 6 | `Content-Disposition: attachment` + `nosniff` | 다운로드 응답 헤더 확인 |
| 7 | 파일 크기 상한 | `spring.servlet.multipart.max-file-size` 설정 확인 |
| 8 | 저장 경로를 웹 루트 밖에 배치 | 정적 리소스 경로로 직접 접근 시 404 |

```yaml
# application.yml
spring:
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 60MB

app:
  storage:
    root: /var/lib/board/uploads   # 웹 루트 밖 (항목 8)
```

## 6.4 좋아요

```java
@Transactional
public LikeResult like(Long postId, Long memberId) {
    if (postLikeRepository.existsById(new PostLikeId(postId, memberId))) {
        return LikeResult.alreadyLiked();
    }
    postLikeRepository.save(new PostLike(postId, memberId));
    postRepository.increaseLikeCount(postId);   // 원자적 UPDATE
    return LikeResult.liked();
}
```

`post_like`의 복합 PK가 중복을 DB 레벨에서 차단합니다. 경합 상황에서 `existsById` 검사를 통과해도 `DataIntegrityViolationException`으로 막히므로 이중 방어가 됩니다.

## 6.5 페이징

게시판 규모에서는 offset 페이징으로 충분합니다. 커서 페이징은 이 요구사항에 오버엔지니어링입니다.

```java
public PageResponse<PostSummary> list(String boardSlug, int page, int size, String keyword) {
    Pageable pageable = PageRequest.of(page - 1, size, Sort.by(DESC, "id"));
    Page<Post> result = postRepository.search(boardSlug, keyword, pageable);
    return PageResponse.from(result);
}
```

응답 형식은 프론트가 페이지 번호를 그리기에 충분해야 합니다.

```json
{
  "content": [ ... ],
  "page": 1,
  "size": 10,
  "totalElements": 137,
  "totalPages": 14
}
```

## 6.6 비회원 글 권한 검증 절차 (D1)

### 작성

```java
@Transactional
public Long createGuestPost(String boardSlug, GuestPostCommand cmd) {
    Board board = boardRepository.findBySlug(boardSlug).orElseThrow();
    board.checkWritable(Optional.empty());     // requiresAuthToWrite=false 인 게시판만 통과

    Post post = Post.guest(board,
        cmd.guestNickname(),
        encoder.encode(cmd.guestPassword()),   // 반드시 해시
        cmd.title(), cmd.content());

    return postRepository.save(post).getId();
}
```

### 수정·삭제

```java
@Transactional
public void delete(Long postId, Optional<Member> actor, String rawGuestPassword) {
    Post post = postRepository.findById(postId).orElseThrow();
    post.checkEditable(actor, rawGuestPassword, encoder);   // 관리자 → 소유자 → 비밀번호 순
    post.softDelete();
}
```

### 프론트엔드 흐름

```
비회원 글 상세
   │
   ├─ [수정] / [삭제] 클릭
   │       │
   │       ▼
   │   비밀번호 입력 모달
   │       │
   │       ▼
   │   PUT/DELETE 요청 본문에 guestPassword 포함
   │       │
   │       ├─ 200 → 성공
   │       └─ 403 → "권한이 없거나 비밀번호가 일치하지 않습니다"
   │
   └─ 관리자로 로그인한 경우
           비밀번호 모달을 띄우지 않고 바로 요청
```

### 무차별 대입 방어

작성자 비밀번호는 보통 4~6자리로 짧게 입력됩니다. 최소한의 방어가 필요합니다.

- 게시물당 비밀번호 시도 횟수 제한 (예: 5회 실패 시 10분 잠금)
- 실패 응답 시간을 일정하게 유지 (타이밍 공격 방지)
- 최소 길이 4자 이상 강제

> 이 방어는 필수는 아니지만, 운영에 올릴 경우 반드시 넣으십시오. 학습 단계에서는 최소 길이 검증만 두고 나머지는 TODO로 남겨도 무방합니다.

---

# 7. 프론트엔드 구성

## 7.0 UI 컴포넌트 기준 — COSS UI로 DaisyUI 교체

현재 `frontend-react`는 Tailwind CSS v3와 DaisyUI 4를 사용하고 있다. UI 컴포넌트는 COSS UI로 단계적으로 교체하며, 새 화면에는 DaisyUI 전용 클래스(`btn`, `card`, `navbar`, `dropdown` 등)를 추가하지 않는다. 교체 실행 계획은 `docs/plan.md`에 관리한다.

### COSS Skills 설치

```powershell
cd frontend-react
npx skills add cosscom/coss
```

COSS Skills는 AI 코딩 보조 도구에 COSS UI 컴포넌트 API, 조합 패턴, 스타일 규칙, 마이그레이션 규칙을 제공한다. Skills 설치만으로 실제 UI 컴포넌트가 프로젝트에 추가되는 것은 아니므로, COSS UI 컴포넌트 설치 절차는 별도로 수행한다.

### UI-백엔드 기능 계약

회원가입 화면에 표시되는 인증 상태는 클라이언트 상태만으로 확정하지 않는다. 회원가입 요청은 다음 계약을 사용한다.

- `POST /api/members/signup`: `username`, `password`, `name`, `email`, `phone`, `captchaToken`, 필수 약관 동의 정보를 받는다.
- 서버는 회원 생성 전에 CAPTCHA provider를 호출해 토큰 유효성·만료·요청 맥락을 검증한다.
- CAPTCHA secret은 환경 변수 또는 외부 설정으로 주입하고, 토큰 원문은 저장하거나 로그에 남기지 않는다.
- CAPTCHA 검증 실패·provider 장애·토큰 만료는 일반화된 오류로 반환하며 회원을 생성하지 않는다.
- `POST /api/members/verify-email`: 이메일 인증 토큰을 1회 검증하고 `PENDING` 회원을 `ACTIVE`로 전환한다.
- `POST /api/members/verify-email/resend`: 만료되지 않은 정책과 rate limit을 적용해 인증 메일을 재발송한다.
- 비밀번호 찾기·재설정은 임시 비밀번호 만료와 `must_change_password` 정책을 따른다.
- Google 로그인은 OAuth provider 계약이 확정되기 전까지 제공하지 않으며, UI에 가짜 성공 경로를 두지 않는다.

세부 실행 계획은 `docs/plan.md`에 기록한다.

### 적용 원칙

- 공통 레이아웃을 먼저 교체한 뒤 페이지별 UI를 교체한다.
- COSS의 semantic color, Tailwind 토큰, `data-slot`, `render` 기반 트리거 조합을 따른다.
- Dialog는 Header·Panel·Footer 구조를 사용하고, 취소 버튼은 `variant="ghost"`를 우선한다.
- 서버 상태는 TanStack Query, 페이지·게시판 slug·검색어는 URL `searchParams`, 임시저장만 Zustand persist에 둔다.
- 비회원 게시글 비밀번호는 Dialog의 지역 상태로만 보관하고 요청 직후 폐기한다. Zustand나 URL에 저장하지 않는다.
- API 성공·실패와 인증 오류는 Toast 또는 Alert로 사용자에게 표시한다.
- 첨부파일은 확장자나 클라이언트 `Content-Type`을 추측하지 않고 서버가 반환한 `mediaKind`만 사용한다.

### 화면별 컴포넌트 매핑

| 화면 또는 기능 | 적용 대상 |
|---|---|
| 로그인·회원가입 | Form, Field, Input, Button, Toast |
| 게시판 목록·검색 | Table, Input, Button, Pagination, Empty State |
| 게시글 상세 | Card 구성, Button, Dialog, Toast |
| 비회원 수정·삭제 | Password Field, Dialog, Button |
| 댓글 | Textarea, Form, Button, Toast |
| 자료실 첨부파일 | Field, 파일 입력, 서버 `mediaKind`별 렌더러 |
| 공통 레이아웃 | Navbar, Dropdown/Menu, Navigation Button |

### 단계적 마이그레이션 순서

1. COSS UI 기반과 Tailwind 설정의 호환성을 확인한다.
2. `RootLayout`의 Navbar, 사용자 메뉴, 로그인·회원가입 버튼을 교체한다.
3. `HomePage`, `BoardPage`의 카드·검색·목록·페이지네이션을 교체한다.
4. `PostPage`, `PostEditPage`, `LoginPage`의 본문·폼·댓글·좋아요 UI를 교체한다.
5. 비회원 비밀번호 Dialog, 관리자 우회, Toast 오류 처리를 요구사항에 맞게 확인한다.
6. DaisyUI 의존성, Tailwind 플러그인, DaisyUI 전용 클래스를 제거한다.
7. `npm run lint`와 `npm run build`를 실행하고 기존 기능 흐름을 검증한다.

### 전환 완료 기준

- `package.json`, lockfile, Tailwind 설정과 소스에 DaisyUI 의존성 및 전용 클래스가 없다.
- 로그인, 게시판 목록·검색·페이지 이동, 게시글 CRUD, 댓글, 좋아요 흐름이 유지된다.
- 관리자는 비회원 게시글 삭제 시 비밀번호를 요구받지 않는다.
- 비회원 비밀번호가 전역 상태나 URL에 남지 않는다.
- API 및 인증 오류가 사용자에게 표시된다.

## 7.1 폴더 구조

```
frontend-react/                     package.json name: page.sanotehu.board.frontend
└── src/
    ├── app/
    │   ├── router.tsx
    │   └── providers.tsx          QueryClientProvider
    ├── shared/
    │   ├── api/client.ts          fetch 래퍼 + CSRF 헤더
    │   └── ui/
    ├── features/
    │   ├── auth/                  useCurrentUser, useLogin, useSignup, useFindPassword
    │   ├── board/                 useBoards
    │   ├── post/                  usePostList, usePost, useCreatePost, useLikePost,
    │   │                          GuestPasswordDialog
    │   ├── comment/               useComments, useCreateComment
    │   └── attachment/            AttachmentView, useUpload
    └── stores/                    Zustand (최소)
```

## 7.2 Query Key 팩토리

```ts
export const authKeys = {
  me: ['me'] as const,
}

export const boardKeys = {
  all: ['boards'] as const,
}

export const postKeys = {
  all: ['posts'] as const,
  list: (board: string, p: ListParams) => [...postKeys.all, 'list', board, p] as const,
  detail: (id: number) => [...postKeys.all, 'detail', id] as const,
}

export const commentKeys = {
  byPost: (postId: number) => ['comments', postId] as const,
}
```

글 작성 후 `invalidateQueries({ queryKey: postKeys.all })` 하나로 모든 페이지의 목록 캐시가 무효화됩니다.

## 7.3 인증 상태 — 단일 진실 원천

```ts
export function useCurrentUser() {
  return useQuery({
    queryKey: authKeys.me,
    queryFn: () => api.get<Me>('/api/me'),
    staleTime: 5 * 60 * 1000,
    retry: false,          // 401은 재시도하지 않는다
  })
}
```

**사용자 정보를 Zustand에 복사하지 않습니다.** 진실 원천이 둘이 되면 세션 만료 시 반드시 어긋납니다. 로그아웃은 `queryClient.clear()` 한 줄로 전체 캐시를 폐기합니다.

`Me` 응답에는 `mustChangePassword`와 `role`이 포함됩니다. 관리자 여부에 따라 비회원 글의 비밀번호 모달을 건너뛰는 분기(6.6절)에 사용합니다.

## 7.4 목록 상태는 URL에

```tsx
const [sp, setSp] = useSearchParams()
const page = Number(sp.get('page') ?? 1)
const keyword = sp.get('q') ?? ''

const { data } = useQuery({
  queryKey: postKeys.list(boardSlug, { page, keyword }),
  queryFn: () => api.get(`/api/boards/${boardSlug}/posts`, { params: { page, keyword } }),
  placeholderData: keepPreviousData,   // 페이지 이동 시 깜빡임 제거
})
```

새로고침, 뒤로가기, 링크 공유, 북마크가 전부 동작합니다. 게시판에서 "검색 결과 3페이지 링크 공유"는 실제 요구사항입니다.

## 7.5 좋아요 — 낙관적 업데이트

```ts
export function useLikePost() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (postId: number) => api.post(`/api/posts/${postId}/like`),
    onMutate: async (postId) => {
      await qc.cancelQueries({ queryKey: postKeys.detail(postId) })
      const prev = qc.getQueryData<Post>(postKeys.detail(postId))
      qc.setQueryData<Post>(postKeys.detail(postId), (old) =>
        old ? { ...old, likeCount: old.likeCount + 1, likedByMe: true } : old)
      return { prev }
    },
    onError: (_e, postId, ctx) =>
      qc.setQueryData(postKeys.detail(postId), ctx?.prev),
    onSettled: (_d, _e, postId) =>
      qc.invalidateQueries({ queryKey: postKeys.detail(postId) }),
  })
}
```

`onMutate`에서 `cancelQueries`를 먼저 호출하는 이유는, 진행 중이던 조회 응답이 나중에 도착해 낙관적 값을 덮어쓰는 것을 막기 위해서입니다.

## 7.6 첨부파일 렌더링

```tsx
function AttachmentView({ file }: { file: Attachment }) {
  const src = `/api/files/${file.id}`
  switch (file.mediaKind) {
    case 'IMAGE': return <img src={src} alt={file.originalName} />
    case 'VIDEO': return <video src={src} controls />
    case 'AUDIO': return <audio src={src} controls />
    default:      return <a href={`${src}?download=true`}>{file.originalName}</a>
  }
}
```

확장자를 파싱하지 않습니다. 서버가 판정한 `mediaKind`만 신뢰합니다.

## 7.7 비회원 글 수정·삭제 흐름 (D1)

```tsx
function GuestPostActions({ post }: { post: Post }) {
  const { data: me } = useCurrentUser()
  const [dialogOpen, setDialogOpen] = useState(false)
  const remove = useDeletePost()

  const isAdmin = me?.role === 'ADMIN'

  const handleDelete = () => {
    if (isAdmin) {
      remove.mutate({ postId: post.id })          // 관리자는 비밀번호 불필요
    } else {
      setDialogOpen(true)                          // 비회원은 비밀번호 확인
    }
  }

  return (
    <>
      <button onClick={handleDelete}>삭제</button>
      <GuestPasswordDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onSubmit={(guestPassword) => remove.mutate({ postId: post.id, guestPassword })}
      />
    </>
  )
}
```

**비밀번호는 절대 상태에 오래 남기지 않습니다.** 모달에서 받아 요청에 실은 뒤 즉시 폐기합니다. Zustand나 URL에 넣지 마십시오.

## 7.8 API 클라이언트 (CSRF)

```ts
function csrfToken() {
  return document.cookie.split('; ')
    .find(c => c.startsWith('XSRF-TOKEN='))?.split('=')[1]
}

export const api = {
  async request<T>(url: string, init: RequestInit = {}): Promise<T> {
    const res = await fetch(url, {
      ...init,
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json',
        ...(init.method && init.method !== 'GET'
          ? { 'X-XSRF-TOKEN': decodeURIComponent(csrfToken() ?? '') }
          : {}),
        ...init.headers,
      },
    })
    if (res.status === 401) { /* 로그인 페이지로 리다이렉트 */ }
    if (!res.ok) throw await ApiError.from(res)
    return res.status === 204 ? (undefined as T) : res.json()
  },
}
```

## 7.9 Zustand 스토어 목록

| 상태 | 배치 |
|---|---|
| 게시글, 댓글, 첨부, 회원정보, 좋아요 수 | TanStack Query |
| 페이지 번호, 게시판 slug, 검색어 | URL searchParams |
| **아이디 저장** | 명세가 **쿠키**를 지정 → Zustand 불필요 |
| **비회원 작성자 비밀번호** | 모달 지역 `useState` → 요청 후 즉시 폐기 |
| **글 작성 중 임시저장** | Zustand + persist — **스토어 1개** |
| 모달, 드롭다운, 첨부 미리보기 토글 | `useState` |

**전역 클라이언트 스토어는 1개입니다.** 요구사항 규모에 비해 작아 보이지만, 이것이 정상입니다.

```ts
export const useDraftStore = create<DraftState>()(
  persist(
    (set, get) => ({
      drafts: {} as Record<string, string>,
      save: (key, content) => set((s) => ({ drafts: { ...s.drafts, [key]: content } })),
      read: (key) => get().drafts[key],
      clear: (key) => set((s) => {
        const { [key]: _, ...rest } = s.drafts
        return { drafts: rest }
      }),
    }),
    { name: 'board-draft' }
  )
)
```

> **임시저장에 비밀번호를 담지 마십시오.** `persist`는 localStorage에 평문으로 남습니다. 저장 대상은 제목과 본문뿐입니다.

### Zustand v5 셀렉터 주의

```ts
// 나쁨 — 매 렌더마다 새 객체 → Object.is 비교 실패 → 무한 리렌더
const { drafts, save } = useDraftStore((s) => ({ drafts: s.drafts, save: s.save }))

// 좋음
import { useShallow } from 'zustand/react/shallow'
const { drafts, save } = useDraftStore(useShallow((s) => ({ drafts: s.drafts, save: s.save })))

// 더 좋음 — 원시값 단위로 분리
const save = useDraftStore((s) => s.save)
```

---

# 8. 개발 환경 구성

## 8.1 프로젝트 구조 — 하나의 저장소, 두 개의 IDE

세 가지 경계를 구분합니다.

| 경계 | 의미 | 이 프로젝트 |
|---|---|---|
| **VCS 경계** | Git 저장소 개수 | 1개 (모노레포) |
| **빌드 경계** | `settings.gradle` 포함 여부 | 1개 (Gradle 멀티프로젝트) |
| **IDE 경계** | `.idea/` 개수 | **2개** (루트 = IntelliJ, `frontend-react/` = WebStorm) |

```
website-kim-b-simple/
├── settings.gradle
├── build.gradle
├── buildSrc/
│   └── src/main/groovy/board.java-conventions.gradle
├── backend-springboot/
│   ├── build.gradle
│   └── src/main/java/page/sanotehu/board/backend/...
│   └── src/main/resources/db/migration/V1__init.sql
└── frontend-react/
    ├── build.gradle           ← Node 빌드를 감싸는 Gradle 래퍼
    ├── package.json           ← name: page.sanotehu.board.frontend
    ├── vite.config.ts
    └── src/
```

```groovy
// settings.gradle
rootProject.name = 'website-kim-b-simple'
include 'backend-springboot'
include 'frontend-react'
```

## 8.2 frontend-react/build.gradle

```groovy
plugins {
    id 'base'
    id 'com.github.node-gradle.node' version '7.1.0'
}

node {
    version = '22.20.0'
    download = true              // 로컬 Node 설치와 무관 → 재현 가능한 빌드
    nodeProjectDir = layout.projectDirectory
}

def distDir = layout.buildDirectory.dir('dist')

def buildFrontend = tasks.register('buildFrontend',
        com.github.gradle.node.npm.task.NpmTask) {
    dependsOn tasks.npmInstall
    args = ['run', 'build']
    inputs.dir('src').withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files('package.json', 'package-lock.json', 'vite.config.ts', 'tsconfig.json')
    outputs.dir(distDir)
    outputs.cacheIf { true }     // 소스 미변경 시 npm run build 스킵
}

configurations {
    frontendAssets { canBeConsumed = true; canBeResolved = false }
}

artifacts {
    frontendAssets(distDir.get().asFile) { builtBy buildFrontend }
}

tasks.named('assemble') { dependsOn buildFrontend }
```

`download = true`가 중요합니다. 팀원 간 Node 버전 편차에서 오는 "제 컴퓨터에선 되는데요" 문제를 제거합니다.

## 8.3 backend-springboot/build.gradle — 프론트 산출물 흡수

```groovy
configurations {
    frontendAssets { canBeConsumed = false; canBeResolved = true }
}

dependencies {
    // v1.0에서 ':frontend' 로 잘못 적혀 있었음 (F3)
    frontendAssets project(path: ':frontend-react', configuration: 'frontendAssets')
}

tasks.named('processResources') {
    from(configurations.frontendAssets) { into 'static' }
}
```

producer/consumer configuration으로 연결하면 다른 프로젝트의 내부 경로를 알 필요가 없습니다. Gradle 9 이후 project isolation 방향에서 크로스 프로젝트 `layout.buildDirectory` 접근에 제약이 생기므로 이 방식이 안전합니다.

## 8.4 vite.config.ts

```ts
export default defineConfig({
  plugins: [react()],
  build: { outDir: 'build/dist', emptyOutDir: true },
  server: {
    port: 5173,
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
  },
})
```

### 왜 CORS가 아니라 프록시인가

프록시는 **same-origin을 유지**합니다. 세션 쿠키, `SameSite`, CSRF 토큰이 개발 환경과 운영 환경에서 동일하게 동작합니다. CORS + `credentials: 'include'`로 개발하면 운영에서만 터지는 인증 버그가 발생합니다.

## 8.5 SPA 딥링크 처리

```java
package page.sanotehu.board.backend.config;

@Configuration
class SpaResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(new PathResourceResolver() {
                @Override
                protected Resource getResource(String path, Resource location)
                        throws IOException {
                    if (path.startsWith("api/")) return null;   // API 미스는 404 유지
                    Resource requested = location.createRelative(path);
                    return (requested.exists() && requested.isReadable())
                        ? requested
                        : new ClassPathResource("static/index.html");
                }
            });
    }
}
```

`api/` 가드가 없으면 존재하지 않는 API 엔드포인트가 `200` + HTML을 반환해 프론트엔드 에러 처리가 조용히 망가집니다.

## 8.6 IDE 구성

| IDE | 여는 경로 | 생성되는 설정 |
|---|---|---|
| IntelliJ IDEA | `website-kim-b-simple/` (Gradle 프로젝트) | `website-kim-b-simple/.idea/` |
| WebStorm | `website-kim-b-simple/frontend-react/` | `website-kim-b-simple/frontend-react/.idea/` |

**같은 루트를 두 IDE로 동시에 열지 마십시오.** 하나의 `.idea/`를 공유하면서 `modules.xml`, `misc.xml`, `workspace.xml`을 서로 덮어써 모듈 정의가 사라지거나 인덱스가 꼬입니다.

```gitignore
.idea/
frontend-react/.idea/
frontend-react/node_modules/
frontend-react/build/
backend-springboot/build/
```

IntelliJ에서 **Compound Run Configuration**으로 `bootRun`과 `npm run dev`를 함께 실행하면 편리합니다.

## 8.7 Docker Compose (PostgreSQL)
프로젝트 구동에 필수적인 PostgreSQL 서버를 띄울 수 있도록, 프로젝트 루트에 `docker-compose.yml`이 제공됩니다.

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:15-alpine
    container_name: board-postgres
    environment:
      POSTGRES_USER: board_user
      POSTGRES_PASSWORD: board_password
      POSTGRES_DB: board_db
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    restart: unless-stopped
```

---

# 9. 구현 순서

| 단계 | 범위 | 이 단계에서 자리잡는 것 |
|---|---|---|
| 1 | Flyway 스키마 + `board` 시드 데이터 | DB 마이그레이션 습관 |
| 2 | 회원가입 · 로그인 · 로그아웃 (이메일 인증 없이) | Security 설정, 세션, CSRF |
| 3 | **자유게시판** CRUD + 페이징 | 인증 없는 CRUD·페이징 뼈대, **비회원 비밀번호 검증(D1)** |
| 4 | **Q&A 게시판** | 인증 + 소유권 검증, `Board.checkWritable()` 판정 |
| 5 | 댓글 | 부모-자식 관계, 소프트 삭제 |
| 6 | 조회수 하이브리드 추적 + 좋아요 | **전략 패턴(D2)**, 원자적 UPDATE, 낙관적 업데이트 |
| 7 | **자료실** (파일 업로드) | 포트/어댑터, **보안 체크리스트 8항목(D4)** |
| 8 | 이메일 인증 + 비밀번호 찾기 | `@TransactionalEventListener`, `@Async` |

## 순서의 근거

- **자유게시판을 먼저** — 인증이라는 변수를 뺀 상태로 CRUD·페이징·에러 처리의 뼈대를 완성할 수 있습니다. D1의 비회원 비밀번호 검증도 여기서 자리잡습니다.
- **조회수를 4·5단계 뒤에** — 하이브리드 추적(D2)은 "로그인 사용자"와 "익명 사용자"가 모두 존재해야 양쪽 경로를 검증할 수 있습니다. 2단계(로그인)와 3단계(익명 게시판)가 끝난 뒤여야 의미 있는 테스트가 됩니다.
- **자료실과 이메일을 마지막에** — 둘 다 외부 시스템(파일 시스템, SMTP)이 개입해 실패 모드가 다릅니다. 앞의 뼈대가 없으면 디버깅 대상이 두 배가 됩니다.

### 프론트엔드 UI 전환 순서

DaisyUI 교체는 기능 구현과 분리해 공통 UI부터 진행한다. 먼저 COSS UI 기반과 Tailwind 호환성을 확인하고 `RootLayout`을 교체한 뒤, 게시판 목록·검색, 게시글 상세·작성, 인증·댓글 순으로 진행한다. 각 단계에서 TanStack Query, URL `searchParams`, Zustand의 상태 경계를 유지하며, 마지막 단계에 DaisyUI 의존성과 전용 클래스를 제거한다. 세부 작업 목록은 `docs/plan.md`를 따른다.

## 6단계 완료 조건 (D2 검증)

| 시나리오 | 기대 결과 |
|---|---|
| 익명으로 글 열람 → 새로고침 5회 | 조회수 +1 |
| 익명으로 열람 → 쿠키 삭제 → 재열람 | 조회수 +1 (쿠키 방식의 알려진 한계) |
| 로그인 후 열람 → 새로고침 5회 | 조회수 +1 |
| 로그인 후 열람 → 쿠키 삭제 → 재열람 | 조회수 **증가 없음** |
| 로그인 후 PC에서 열람 → 모바일에서 열람 | 조회수 **증가 없음** |
| 자정 이후 재열람 | 조회수 +1 |
| 동시 요청 10건 | 조회수 +1 (경합 없음) |

## 7단계 완료 조건 (D4 검증)

6.3절 보안 체크리스트 8항목의 "확인 방법" 열을 그대로 테스트 케이스로 사용합니다.

---

# 10. 잔여 미결정 사항

v1.0의 6건 중 1건(자유게시판 수정·삭제)이 D1으로 확정되어 제외됐고, 새 항목 1건이 추가되어 총 6건입니다.

| # | 항목 | 선택지 | 영향 범위 | 우선도 |
|---|---|---|---|---|
| 1 | **회원제 게시판의 읽기 권한** | A: 익명도 읽기 가능 (현재 시드) / B: 읽기부터 로그인 필수 | `board.requires_auth_to_read` 시드값, 조회수 경로 비중 | **높음** — 2.6과 직결 |
| 2 | 임시 비밀번호 | 기존 비밀번호 덮어쓰기 / 별도 컬럼에 30분 병행 | `member` 테이블 | 높음 |
| 3 | 미인증 계정 만료 | 24시간 후 삭제 / 무기한 보관 | 배치 잡 필요 여부 | 중간 |
| 4 | 파일 저장 위치 | 로컬 디스크 / 오브젝트 스토리지 | `StoreFilePort` 구현체 | 낮음 |
| 5 | 비회원 삭제 API 형태 | `DELETE` + 요청 본문 / `POST /posts/{id}/delete` | `PostController`, 프론트 호출부 | 중간 |
| 6 | 회원 탈퇴 시 게시글 | 익명화 / 유지 / 소프트 삭제 | `post.member_id` FK 정책 | 중간 |
| 7 | 관리자 기능 범위 | 명세에 없음 — 필요 여부 확인 | `MemberRole`, 별도 API | 중간 |

## 각 항목의 판단 재료

**#1 회원제 게시판의 읽기 권한** — 원문 요구("로그인 인증 후 접근 가능한 게시판")를 문자 그대로 만족시키려면 B입니다. B를 택하면 Q&A·자료실 조회는 100% DB 기록 경로를 타므로 2.6의 하이브리드가 명확하게 나뉩니다. A를 택하면 검색 유입과 비회원 열람이 가능해 실사용에는 유리하지만, 익명 조회는 쿠키 방식의 한계를 그대로 받습니다. **스키마는 이미 분리해 뒀으므로 시드 데이터 한 줄로 전환됩니다.**

**#2 임시 비밀번호** — 별도 컬럼(`temp_password_hash`)을 두고 30분간 기존 비밀번호와 병행 허용하는 쪽이 안전합니다. 메일이 도착하지 않아도 사용자가 기존 비밀번호로 로그인할 수 있기 때문입니다. 4.1의 DDL에는 `temp_password_expires_at`만 넣어 뒀으므로, 이 안을 택하면 `temp_password_hash` 컬럼을 추가하는 마이그레이션이 필요합니다.

**#4 파일 저장 위치** — `StoreFilePort`를 두었으므로 로컬 디스크로 시작해도 나중에 무리 없이 전환할 수 있습니다.

**#5 비회원 삭제 API** — `DELETE`에 본문을 싣는 것은 스펙상 허용되지만 일부 프록시·HTTP 클라이언트가 본문을 버립니다. 확실히 하려면 `POST /api/posts/{id}/delete`가 안전합니다. REST 순수성보다 동작 보장을 우선할지 결정이 필요합니다.

---

# 11. 부록 — 상태 관리 판단 트리

프론트엔드에서 새 상태가 필요할 때마다 위에서부터 순서대로 적용합니다.

```
새로운 상태가 필요하다
│
├─ 서버가 소유하는 데이터인가?
│    예 → useQuery / useMutation                    ← 대부분 여기서 끝
│
├─ 새로고침·뒤로가기·링크 공유 시 유지돼야 하는가?
│    예 → URL searchParams 또는 라우트
│
├─ 한 화면 안에서만 쓰이고, 공통 부모가 존재하는가?
│    예 → useState (필요하면 부모로 끌어올리기)
│
├─ 브라우저를 닫았다 열어도 남아야 하는가?
│    예 → Zustand + persist
│
└─ 여기까지 전부 아니오 → Zustand
```

> **비밀번호·토큰은 이 트리를 타지 않습니다.** 어느 분기에도 넣지 말고 요청에 실은 뒤 즉시 폐기합니다.

## 스토어 이름으로 진단하기

| 이름이 이렇다면 | 정체 | 가야 할 곳 |
|---|---|---|
| `usePostStore`, `useMemberStore` | **도메인 명사** = 서버 데이터 유출 | TanStack Query |
| `useFilterStore`, `usePaginationStore` | 목록 조건 | URL searchParams |
| `usePostDetailStore` (한 화면 전용) | 화면 지역 상태 | `useState` |
| `useDraftStore` | **UI·행위 이름** = 진짜 클라이언트 상태 | 그대로 둔다 |

건강한 클라이언트 상태 스토어는 도메인 명사가 아니라 UI 관심사 이름을 가집니다.

## 스토어 간 참조는 경계 오류의 신호

```ts
// 위험 신호
const useCommentDraftStore = create((set) => ({
  add: (text: string) => {
    const user = useMemberStore.getState().user   // ← 다른 스토어를 읽는다
  },
}))
```

스토어 간 의존이 생겼다면 대개 둘 중 하나가 애초에 스토어일 필요가 없습니다. 위 예시라면 `user`는 TanStack Query에 있어야 합니다.

---

*문서 끝 — v2.0*