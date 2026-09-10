# 데이터 보존 정책

> 상태: **현황은 `확인됨`, 정책은 `제안`입니다.**
> 저장소에 보존 기간을 정의한 코드·문서·배치가 **하나도 없습니다.**

## 현황 요약 `확인됨`

| 데이터 | 삭제 경로 | 자동 정리 | 실제 보존 |
|---|---|---|---|
| 회원 | 없음 (`DELETED` 전환 코드 없음) | 없음 | 영구 |
| 게시글 | 소프트 삭제 | 없음 | 영구 |
| 댓글 | 소프트 삭제 | 없음 | 영구 |
| 첨부 메타데이터 | 없음 | 없음 | 영구 |
| 첨부 실제 파일 | 없음 | 없음 | 영구 |
| 좋아요 | 취소 시 하드 삭제 | — | 취소까지 |
| 조회 기록 | 없음 | 없음 | 영구 |
| 인증 토큰 | 없음 | 없음 | 영구 |
| 세션 | 컨테이너 만료 | — | 재시작 시 소멸 |
| 로그 | stdout | 없음 | 컨테이너 수명 |

**하드 삭제되는 것은 좋아요 취소 하나뿐입니다.**

## 소프트 삭제가 남기는 것 `확인됨`

게시글을 삭제해도 다음이 그대로 남습니다.

```
post 행           deleted_at 만 설정, 제목·본문 그대로
attachment 행     남음 — ON DELETE CASCADE 는 행 삭제 시에만 발동
디스크 파일        남음
post_like 행      남음
post_view_log 행  남음
comment 행        남음 (deleted_at 조차 설정되지 않음)
```

### CASCADE가 죽어 있다 `확인됨`

`attachment`, `post_like`, `post_view_log`가 `ON DELETE CASCADE`로 선언되어 있지만
**소프트 삭제는 행을 지우지 않으므로 발동하지 않습니다.**

스키마를 보면 정리될 것처럼 보이는데 실제로는 정리되지 않습니다.
오해를 부르는 지점입니다.

### 댓글이 함께 삭제 표시되지 않는다 `확인됨`

글을 삭제해도 `comment.deleted_at`은 그대로 `NULL`입니다.
댓글 조회가 글 존재를 먼저 확인하므로(`CommentService.getComments:30`)
화면에는 드러나지 않지만, DB에는 살아 있는 댓글로 남습니다.

`GET /api/me/comments`는 글 존재를 확인하지 않으므로
**삭제된 글의 댓글이 마이페이지에 계속 보입니다.** `미결정`

## 무한히 자라는 것 `확인됨`

### 1. `post_view_log` — 가장 빠름

| 항목 | 값 |
|---|---|
| 증가 요인 | 회원 × 글 × 활동일수 |
| 정리 | 없음 |
| 예시 | 활동 회원 100명 × 하루 20글 = 하루 2,000행, 연 73만 행 |

원래 목적(조회수 중복 방지)에는 **오늘 날짜만** 필요합니다.
어제 이전 기록은 조회수 판정에 쓰이지 않습니다.

`idx_view_log_date (viewed_on)`가 있는 것을 보면
날짜 기준 정리를 염두에 뒀던 것으로 보이나 구현되지 않았습니다.

### 2. `verification_token` — 악용 가능

| 항목 | 값 |
|---|---|
| 증가 요인 | 가입 + 인증 재발송 + 비밀번호 재설정 요청 |
| 정리 | 없음 |
| 위험 | **재발송에 횟수 제한이 없음** |

`POST /api/members/verify-email/resend`를 반복 호출하면
행이 계속 쌓이고 **모두 유효한 상태로 공존합니다**(기존 토큰을 무효화하지 않음).

만료·사용된 토큰은 아무 가치가 없는데 영구 보존됩니다.

### 3. 업로드 파일 — 고아 파일 포함

| 상황 | 결과 |
|---|---|
| 업로드 후 글 저장 안 함 | 파일만 남고 `attachment` 행 없음 |
| 글 소프트 삭제 | 파일·행 모두 남음 |
| 비로그인 대량 업로드 | 인증이 없어 누구나 가능 |

`POST /api/files`에 인증이 없고 100MB까지 허용되므로
**디스크를 채우는 공격이 가능합니다.** `미결정`
→ [../../security/threat-model.md](../../security/threat-model.md) T-002

## 제안하는 보존 정책 `제안`

아래는 초안입니다. 법적 보존 의무는 법무 검토가 필요합니다.

### 즉시 도입 가능 (법적 판단 불필요)

| 대상 | 제안 | 근거 |
|---|---|---|
| `post_view_log` | **90일** 후 삭제 | 조회수 판정에는 당일만 필요. 통계 목적으로 여유 |
| `verification_token` | 만료·사용 후 **30일** 삭제 | 이후 가치 없음 |
| 고아 업로드 파일 | **24시간** 후 삭제 | 글 작성 흐름은 몇 분 안에 끝남 |

세 가지 모두 **사용자에게 보이는 데이터가 아니라** 영향이 없습니다.

구현 예 `제안`:

```sql
-- 조회 기록
DELETE FROM post_view_log WHERE viewed_on < CURRENT_DATE - INTERVAL '90 days';

-- 사용·만료된 토큰
DELETE FROM verification_token
 WHERE (used_at IS NOT NULL AND used_at < now() - INTERVAL '30 days')
    OR (expires_at < now() - INTERVAL '30 days');
```

Spring `@Scheduled`로 하루 한 번 돌리면 충분합니다.
현재 `@EnableScheduling`이 없으므로 함께 추가해야 합니다.

**주의**: 위 `DELETE`는 조건이 명확한 정리 작업입니다.
조건 없는 `DELETE`나 `TRUNCATE`는 프로젝트 `CLAUDE.md`에서 금지된 작업입니다.

### 결정이 필요한 것 `미결정`

| 대상 | 질문 |
|---|---|
| 탈퇴 회원 | 즉시 익명화? 유예 기간? 개인정보만 삭제하고 글은 유지? |
| 삭제된 글·댓글 | 복구 요구를 받는가? 몇 일 뒤 하드 삭제? |
| 삭제된 글의 첨부 파일 | 언제 디스크에서 지우는가? |
| 휴면 계정 | 일정 기간 미접속 시 처리? |
| 접속·감사 로그 | 남기지 않고 있음. 남긴다면 얼마나? |

### 탈퇴 처리가 특히 어려운 이유 `확인됨`

`member` 행을 지우면:

- `verification_token`, `post_like`, `post_view_log`는 CASCADE로 함께 삭제됩니다
- `post.member_id`는 CASCADE가 없어 **FK 위반으로 삭제가 실패합니다**
- `comment.member_id`도 `NOT NULL`이라 마찬가지입니다

즉 **글이나 댓글을 쓴 회원은 현재 스키마에서 삭제할 수 없습니다.**

현실적 선택지 `제안`:

| 방안 | 내용 | 영향 |
|---|---|---|
| A. 익명화 | `status = DELETED` + 개인정보 컬럼을 더미 값으로 | 글·댓글 유지. 구현 간단 |
| B. 글 이관 | 탈퇴회원 계정으로 `member_id` 이관 | 스키마 변경 필요 |
| C. 전체 삭제 | 글·댓글도 함께 삭제 | 다른 사용자의 대화 맥락이 깨짐 |

**A를 권합니다.** 커뮤니티 서비스에서 흔한 방식이고, 스키마 변경이 없습니다.
`username`·`email`을 유니크한 더미 값으로 바꿔 UNIQUE 제약을 유지하면 됩니다.

## 백업 `미결정`

**백업이 없습니다.**

| 대상 | 현재 |
|---|---|
| DB (`board_data` 볼륨) | 백업 없음 |
| 업로드 파일 (`board_uploads` 볼륨) | 백업 없음 |
| 설정 (`.env`) | `.gitignore` 대상이라 저장소에 없음 |

볼륨이 손실되면 전부 사라집니다.
`docker volume rm`을 실수로 실행하면 되돌릴 수 없습니다.

최소한의 제안 `제안`:

```bash
# DB 덤프
docker exec board-postgres pg_dump -U board_user board_db > backup-$(date +%F).sql

# 업로드 파일
docker run --rm -v website-kim-b-simple_board_uploads:/data \
  -v "$PWD":/backup alpine tar czf /backup/uploads-$(date +%F).tar.gz -C /data .
```

## 로그 `확인됨`

stdout으로만 나갑니다. 컨테이너가 재시작하면 사라집니다.

`show-sql: true`라 SQL이 전부 로그에 남습니다 — 운영에서는 꺼야 합니다.
`MAIL_DEBUG=true`면 SMTP 인증 정보까지 남습니다.

로그 안에 개인정보가 들어갑니다(SQL의 이메일·이름 값).
→ [../../security/privacy.md](../../security/privacy.md)

## 우선순위 `제안`

| 순위 | 작업 | 이유 |
|---|---|---|
| 1 | DB·업로드 볼륨 백업 | 손실 시 복구 불가 |
| 2 | 고아 파일 정리 + 업로드 인증 | 디스크 고갈 공격 가능 |
| 3 | `post_view_log` 90일 정리 | 가장 빠르게 자람 |
| 4 | `verification_token` 정리 | 악용 가능 |
| 5 | 탈퇴 처리(익명화) 구현 | 법적 요구에 응할 수단이 없음 |
| 6 | `show-sql` 끄기 | 로그에 개인정보 |

## 관련 문서

- [data-model.md](data-model.md) — 테이블별 증가 특성
- [migration-policy.md](migration-policy.md) — 스키마 변경
- [../../security/privacy.md](../../security/privacy.md) — 개인정보 처리
- [../../security/data-classification.md](../../security/data-classification.md) — 데이터 등급
- [../../business/compliance-matrix.md](../../business/compliance-matrix.md) — 규제 대응
