# 실제 환경 테스트 시나리오

## 테스트 환경
- Frontend: http://localhost:5173 (Vite dev server)
- Backend: http://localhost:8080 (Spring Boot)
- Database: PostgreSQL (192.168.29.124:5432)
- CAPTCHA: Fake mode (dev-captcha token)

## 테스트 케이스

### 1️⃣ must_change_password 강제 변경 플로우

**목표**: 임시 비밀번호로 로그인 후 강제 비밀번호 변경 확인

**시나리오**:
1. POST /api/members/password-reset/issue-temp-password
   - email: test@example.com
   - captchaToken: dev-captcha
2. 메일에서 임시 비밀번호 확인 (로그 출력)
3. POST /auth/login with 임시 비밀번호
   - username: test_user
   - password: [temporary password]
4. GET /api/me → mustChangePassword: true 확인
5. 프론트엔드에서 /change-password로 자동 리다이렉트 확인
6. POST /api/members/change-password with 새 비밀번호
   - newPassword: MyNewPassword123!
7. GET /api/me → mustChangePassword: false 확인

**검증 포인트**:
- ✅ ChangePasswordPage 렌더링
- ✅ 비밀번호 강도 표시 (약함/보통/좋음/강함)
- ✅ 비밀번호 일치 검증
- ✅ mustChangePassword 플래그 해제

---

### 2️⃣ 비밀번호 강도 표시

**목표**: 실시간 비밀번호 강도 계산 확인

**테스트 대상**: /change-password 페이지

**입력 테스트**:
1. "pass" → 약함 (4글자)
2. "MyPass123" → 보통 (9글자, 다양한 문자)
3. "MyNewPassword123!" → 좋음 (16글자, 특수문자)
4. "ComplexPassword12345@#" → 강함 (22글자, 모든 조건)

**검증 포인트**:
- ✅ 진행 막대 색상 변경 (빨강→노랑→파랑→초록)
- ✅ 레이블 업데이트
- ✅ 제출 버튼 활성화 조건 (8자 이상 + 일치)

---

### 3️⃣ 아이디 찾기 CAPTCHA 검증

**목표**: 아이디 찾기 시 CAPTCHA 검증 확인

**시나리오**:
1. /find-username 페이지 접속
2. 이메일 입력: test@example.com
3. CAPTCHA 필드 입력: dev-captcha
4. "아이디 안내 받기" 클릭
5. 메일 발송 확인 (로그 출력)

**검증 포인트**:
- ✅ CAPTCHA 필드 표시
- ✅ CAPTCHA 검증 전까지 제출 버튼 비활성화
- ✅ 성공 메시지 표시

---

### 4️⃣ 로깅 강화 검증

**목표**: 로그에서 보안 관련 이벤트 확인

**확인할 로그**:
1. CAPTCHA 검증 결과
   ```
   DEBUG: CAPTCHA verification (fake mode): result=true
   ```
2. 임시 비밀번호 발급
   ```
   INFO: 임시 비밀번호 발급 완료. memberId=1 email=test@example.com
   ```
3. 메일 발송 성공
   ```
   INFO: 임시 비밀번호 메일 발송 성공. to=test@example.com
   ```

---

### 5️⃣ 고아 파일 정리 (선택)

**목표**: 스케줄러 정상 작동 확인

**확인 방법**:
- 로그에서 "orphaned file cleanup" 관련 로그 확인
- 또는 관리자 API 수동 호출: POST /api/admin/files/cleanup-orphaned

---

### 6️⃣ 성능 최적화 확인

**캐싱**:
1. GET /api/boards 첫 호출 → DB 쿼리
2. GET /api/boards 두 번째 호출 → 캐시에서 반환 (응답 시간 단축)

**비동기 메일**:
- 메일 발송이 요청을 블로킹하지 않음 (응답 시간 200ms 이내)

---

## 테스트 실행 명령어

```bash
# 1. 회원 생성 (테스트용)
curl -X POST http://localhost:8080/api/members/signup \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test_user",
    "password": "TestPassword123!",
    "name": "Test User",
    "email": "test@example.com",
    "phone": "010-1234-5678"
  }'

# 2. 임시 비밀번호 발급
curl -X POST http://localhost:8080/api/members/password-reset/issue-temp-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "captchaToken": "dev-captcha"
  }'

# 3. 현재 사용자 확인
curl -X GET http://localhost:8080/api/me \
  -H "Cookie: JSESSIONID=[session_id]"

# 4. 아이디 찾기
curl -X POST http://localhost:8080/api/members/username-recovery \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "captchaToken": "dev-captcha"
  }'

# 5. 고아 파일 정리 (관리자)
curl -X POST http://localhost:8080/api/admin/files/cleanup-orphaned \
  -H "Cookie: JSESSIONID=[admin_session_id]"
```

---

## 테스트 체크리스트

- [ ] Backend 정상 시작
- [ ] Frontend 접속 가능
- [ ] must_change_password 강제 변경 플로우
- [ ] 비밀번호 강도 표시 (UI)
- [ ] 아이디 찾기 CAPTCHA 검증
- [ ] 로그에 보안 이벤트 기록
- [ ] 성능 최적화 (캐싱, 비동기)
- [ ] 고아 파일 정리 스케줄러 (옵션)

---

## 예상 결과

모든 기능이 정상 작동하면:
- ✅ 보안 강화: must_change_password, CAPTCHA
- ✅ 운영 안정성: 로깅, 고아 파일 정리
- ✅ 성능: 캐싱, 비동기 처리

**상태**: 프로덕션 배포 준비 완료
