# 실제 환경 테스트 가이드

## 🚀 준비 단계

### 필수 실행 환경
```bash
# 터미널 1: Backend (Spring Boot)
cd backend-springboot
./gradlew bootRun

# 터미널 2: Frontend (Vite dev server)  
cd frontend-react
npm run dev

# 터미널 3: Database (PostgreSQL)
# docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=board_password -e POSTGRES_DB=board_db postgres:15
# 또는 기존 인스턴스 사용
```

### 접속 URL
- 🌐 Frontend: http://localhost:5173
- 📡 Backend API: http://localhost:8080
- 🗄️ Database: postgresql://192.168.29.124:5432/board_db

---

## ✅ 테스트 체크리스트

### 1️⃣ must_change_password 강제 변경 플로우 [시간: 5분]

#### 1-1. 임시 비밀번호 발급
```bash
# API 호출
curl -X POST http://localhost:8080/api/members/password-reset/issue-temp-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "captchaToken": "dev-captcha"
  }'
```

**예상 응답:**
```json
{
  "status": "issued"
}
```

**확인 사항:**
- ✅ 로그에 "임시 비밀번호 메일 발송 성공" 출력
- ✅ 로그에 "임시 비밀번호 발급 완료. memberId=..." 출력
- ✅ 로그에 "CAPTCHA verification succeeded" 출력

---

#### 1-2. 임시 비밀번호로 로그인
```
1. http://localhost:5173/login 접속
2. 아이디/이메일: test_user (또는 test@example.com)
3. 비밀번호: [메일에서 확인한 임시 비밀번호]
4. "로그인" 클릭
```

**예상 결과:**
- ✅ 자동으로 /change-password 페이지로 리다이렉트
- ✅ "비밀번호 변경" 페이지 표시
- ✅ "보안을 위해 비밀번호를 변경해야 합니다" 메시지 표시

---

### 2️⃣ 비밀번호 강도 표시 [시간: 3분]

**테스트 페이지:** /change-password

```
테스트 1: "pass"
  → 진행 막대: 약 25% (빨강)
  → 레이블: "약함"

테스트 2: "MyPass123"  
  → 진행 막대: 약 50% (노랑)
  → 레이블: "보통"

테스트 3: "MyNewPassword123!"
  → 진행 막대: 약 75% (파랑)
  → 레이블: "좋음"

테스트 4: "SuperSecurePass123@#$"
  → 진행 막대: 100% (초록)
  → 레이블: "강함"
```

**확인 사항:**
- ✅ 진행 막대 색상이 동적으로 변경
- ✅ 레이블이 실시간 업데이트
- ✅ "비밀번호 변경" 버튼이 다음 조건에서만 활성화:
  - 비밀번호 8자 이상
  - 확인 비밀번호와 일치

---

### 3️⃣ 비밀번호 변경 완료 [시간: 2분]

```
1. 새 비밀번호 입력: MyNewPassword123!
2. 확인 입력: MyNewPassword123!
3. "비밀번호 변경" 클릭
```

**예상 결과:**
- ✅ 로딩 상태 표시
- ✅ 성공 메시지: "비밀번호가 변경되었습니다."
- ✅ 자동으로 /me (마이페이지)로 이동
- ✅ 로그에 "비밀번호 변경 완료" (또는 유사 메시지)

---

### 4️⃣ 아이디 찾기 CAPTCHA 검증 [시간: 3분]

```
1. http://localhost:5173/find-username 접속
2. 이메일 입력: test@example.com
3. CAPTCHA 필드 확인 (이전에는 없었음)
4. CAPTCHA 입력: dev-captcha
5. "아이디 안내 받기" 클릭
```

**확인 사항:**
- ✅ CAPTCHA 필드가 표시됨
- ✅ CAPTCHA 입력 전 버튼은 비활성화
- ✅ CAPTCHA 입력 후 버튼 활성화
- ✅ 성공 메시지 표시: "가입 이메일로 아이디 안내를 전송했습니다."
- ✅ 로그에 "CAPTCHA verification succeeded" 출력

---

### 5️⃣ 로깅 강화 검증 [시간: 2분]

**백엔드 로그 확인:**

```bash
# 터미널에서 로그 필터링
grep -E "CAPTCHA|임시 비밀번호|메일 발송" [backend-output-log]
```

**확인할 로그 메시지:**

```
# 1. CAPTCHA 검증
DEBUG: CAPTCHA verification (fake mode): result=true. remoteAddress=127.0.0.1

# 2. 임시 비밀번호 발급
INFO: 임시 비밀번호 발급 완료. memberId=1 email=test@example.com expiresAt=2026-09-11T...

# 3. 메일 발송 성공
INFO: 임시 비밀번호 메일 발송 성공. to=test@example.com subject=[BoardSystem] 임시 비밀번호 발급
```

---

### 6️⃣ 성능 최적화 확인 [시간: 3분]

#### 캐싱 확인
```bash
# 첫 번째 호출 (DB 쿼리 발생)
time curl -s http://localhost:8080/api/boards

# 두 번째 호출 (캐시에서 반환, 더 빠름)
time curl -s http://localhost:8080/api/boards

# 응답 시간 비교
# 첫 번째: ~50-100ms
# 두 번째: ~5-10ms (캐시 사용)
```

**확인 사항:**
- ✅ 두 번째 요청이 훨씬 빠름

#### 비동기 메일 전송 확인
- ✅ 메일 발송 요청이 200ms 이내에 응답 (블로킹 X)

---

### 7️⃣ 고아 파일 정리 (선택) [시간: 1분]

**관리자 권한으로 수동 정리 테스트:**

```bash
# 관리자로 로그인 후 JSESSIONID 가져오기
curl -X POST http://localhost:8080/api/admin/files/cleanup-orphaned \
  -H "Cookie: JSESSIONID=[admin-session-id]"
```

**예상 응답:**
```json
{
  "status": "cleanup_started"
}
```

**로그 확인:**
```
INFO: Deleted orphaned file: uuid-123.jpg
INFO: Retaining recently uploaded orphaned file: uuid-456.jpg (age: 1500ms)
```

---

## 🎯 전체 테스트 요약

| # | 기능 | 시간 | 상태 |
|---|------|------|------|
| 1 | must_change_password 강제 변경 | 5분 | ⬜ |
| 2 | 비밀번호 강도 표시 | 3분 | ⬜ |
| 3 | 비밀번호 변경 완료 | 2분 | ⬜ |
| 4 | 아이디 찾기 CAPTCHA | 3분 | ⬜ |
| 5 | 로깅 강화 검증 | 2분 | ⬜ |
| 6 | 성능 최적화 | 3분 | ⬜ |
| 7 | 고아 파일 정리 (선택) | 1분 | ⬜ |

**총 예상 시간: 19분**

---

## 🔍 문제 해결

### Backend 접속 불가
```bash
# 포트 8080 확인
netstat -an | grep 8080

# 로그에서 에러 확인
grep -i error [backend-log]

# 데이터베이스 연결 확인
psql -h 192.168.29.124 -U board_user -d board_db
```

### Frontend 접속 불가
```bash
# 포트 5173 확인
netstat -an | grep 5173

# 프론트엔드 dev server 재시작
npm run dev
```

### CAPTCHA 검증 실패
- CAPTCHA_MODE=fake 확인
- CAPTCHA_EXPECTED_TOKEN=dev-captcha 확인
- 로그에서 "CAPTCHA verification failed" 검색

---

## ✨ 예상 최종 결과

모든 테스트가 완료되면:
- ✅ 보안 강화: must_change_password, CAPTCHA
- ✅ UX 개선: 비밀번호 강도 표시
- ✅ 운영 안정성: 로깅, 고아 파일 정리
- ✅ 성능: 캐싱, 비동기 처리

**📦 상태: 프로덕션 배포 준비 완료**
