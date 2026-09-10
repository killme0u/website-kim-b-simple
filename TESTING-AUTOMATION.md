# 자동 테스트 (단위 테스트 & 컴포넌트 테스트)

## 📋 작성된 테스트

### 백엔드 (Spring Boot / JUnit)

#### 1. ChangePasswordTest.java
**파일**: `backend-springboot/src/test/java/.../ChangePasswordTest.java`

**테스트 케이스**:
- ✅ 로그인한 사용자가 비밀번호를 변경할 수 있다
- ✅ 존재하지 않는 사용자는 예외를 발생시킨다
- ✅ 비밀번호 변경 후 Remember-Me 토큰이 제거된다

**실행 명령**:
```bash
# 특정 테스트 클래스만 실행
./gradlew test --tests ChangePasswordTest

# 모든 테스트 실행
./gradlew test
```

#### 2. UsernameRecoveryCaptchaTest.java
**파일**: `backend-springboot/src/test/java/.../UsernameRecoveryCaptchaTest.java`

**테스트 케이스**:
- ✅ CAPTCHA 검증 성공하면 아이디 안내 메일이 발송된다
- ✅ CAPTCHA 검증 실패하면 예외를 발생시킨다
- ✅ 빈 CAPTCHA 토큰은 검증 실패한다
- ✅ CAPTCHA 검증 후에도 가입하지 않은 사용자는 안내 메일을 보내지 않는다

**실행 명령**:
```bash
./gradlew test --tests UsernameRecoveryCaptchaTest
```

---

### 프론트엔드 (React / Vitest)

#### 1. passwordStrength.test.ts
**파일**: `frontend-react/src/__tests__/passwordStrength.test.ts`

**테스트 케이스**:
- ✅ 약한 비밀번호 (4글자, 단일 문자 유형)
- ✅ 보통 비밀번호 (8-11글자, 혼합 문자)
- ✅ 좋은 비밀번호 (12-15글자, 다양한 문자)
- ✅ 강한 비밀번호 (16+글자, 모든 문자 유형)
- ✅ 엣지 케이스 (특수문자, 유니코드, 공백)

**예상 결과**:
```
✓ Password Strength Calculator (30+ tests)
  ✓ weak passwords (3 tests)
  ✓ fair passwords (2 tests)
  ✓ good passwords (2 tests)
  ✓ strong passwords (2 tests)
  ✓ edge cases (3 tests)
```

#### 2. ChangePasswordPage.test.tsx
**파일**: `frontend-react/src/__tests__/ChangePasswordPage.test.tsx`

**테스트 케이스**:
- ✅ mustChangePassword가 true일 때 폼 렌더링
- ✅ mustChangePassword가 false일 때 메시지 표시
- ✅ 비밀번호 강도 인디케이터 표시 (약함/보통/좋음/강함)
- ✅ 폼 유효성 검증 (8자 이상, 일치 여부)
- ✅ 제출 버튼 활성화/비활성화
- ✅ 비밀번호 불일치 오류 메시지

**예상 결과**:
```
✓ ChangePasswordPage (15+ tests)
  ✓ Rendering
  ✓ Password Strength Display
  ✓ Form Validation
```

---

## 🔧 테스트 환경 설정

### 백엔드 (이미 설정됨)
```bash
# build.gradle의 testImplementation에 포함됨
- junit-jupiter (JUnit 5)
- spring-boot-starter-test
- mockito
- assertj
```

### 프론트엔드 (설정 필요)

#### Step 1: 테스트 라이브러리 설치
```bash
cd frontend-react
npm install --save-dev \
  vitest \
  @testing-library/react \
  @testing-library/user-event \
  @testing-library/jest-dom \
  @vitest/ui
```

#### Step 2: vitest.config.ts 생성
```typescript
// frontend-react/vitest.config.ts
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/__tests__/setup.ts'],
    css: true,
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
});
```

#### Step 3: 테스트 셋업 파일 생성
```typescript
// frontend-react/src/__tests__/setup.ts
import '@testing-library/jest-dom';
import { expect, afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

afterEach(() => {
  cleanup();
});

// Mock window.matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation(query => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});
```

#### Step 4: package.json 수정
```json
{
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "test": "vitest",
    "test:ui": "vitest --ui",
    "test:run": "vitest run",
    "lint": "oxlint"
  }
}
```

---

## ▶️ 테스트 실행

### 백엔드 테스트
```bash
cd backend-springboot

# 모든 테스트 실행
./gradlew test

# 특정 테스트만 실행
./gradlew test --tests ChangePasswordTest
./gradlew test --tests UsernameRecoveryCaptchaTest

# 테스트 결과 리포트
cat build/reports/tests/test/index.html
```

### 프론트엔드 테스트 (설치 후)
```bash
cd frontend-react

# 감시 모드로 실행 (파일 변경 시 자동 재실행)
npm run test

# 한 번 실행
npm run test:run

# UI 모드 (대시보드)
npm run test:ui

# 특정 테스트만 실행
npm run test -- passwordStrength
npm run test -- ChangePasswordPage
```

---

## 📊 예상 테스트 커버리지

### 백엔드
```
ChangePasswordTest:
  - VerificationService.changePasswordByUser() 테스트
  - 비밀번호 변경 로직 검증
  - Remember-Me 토큰 제거 확인

UsernameRecoveryCaptchaTest:
  - CAPTCHA 검증 프로세스 테스트
  - 에러 핸들링 검증
  - 사용자 조회 로직 확인
```

### 프론트엔드
```
passwordStrength.test.ts:
  - 강도 계산 알고리즘: 100% 커버리지
  - 모든 문자 유형 조합 테스트
  - 엣지 케이스 처리

ChangePasswordPage.test.tsx:
  - 컴포넌트 렌더링: 100% 커버리지
  - 폼 유효성: 100% 커버리지
  - 사용자 상호작용: 100% 커버리지
```

---

## ✅ 테스트 체크리스트

### 백엔드
- [ ] `./gradlew test` 모든 테스트 통과
- [ ] `ChangePasswordTest` 3개 케이스 통과
- [ ] `UsernameRecoveryCaptchaTest` 4개 케이스 통과
- [ ] 테스트 커버리지 > 80%

### 프론트엔드
- [ ] 테스트 라이브러리 설치 완료
- [ ] vitest.config.ts 생성 완료
- [ ] `npm run test:run` 모든 테스트 통과
- [ ] `passwordStrength.test.ts` 15+ 케이스 통과
- [ ] `ChangePasswordPage.test.tsx` 15+ 케이스 통과

---

## 🎯 CI/CD 통합 (선택)

### GitHub Actions 워크플로우
```yaml
# .github/workflows/test.yml
name: Tests

on: [push, pull_request]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - run: cd backend-springboot && ./gradlew test

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '20'
      - run: cd frontend-react && npm install && npm run test:run
```

---

## 📈 다음 단계

1. **테스트 설정**
   - [ ] Vitest 설정 완료
   - [ ] 테스트 라이브러리 설치

2. **테스트 실행**
   - [ ] 백엔드 테스트 실행 및 통과
   - [ ] 프론트엔드 테스트 실행 및 통과

3. **E2E 테스트 추가** (선택)
   - Playwright 또는 Cypress를 사용한 end-to-end 테스트
   - 실제 브라우저에서 사용자 플로우 검증

4. **CI/CD 설정**
   - GitHub Actions 워크플로우 추가
   - 풀 리퀘스트 시 자동 테스트 실행

---

## 📚 참고 자료

- [Spring Boot Testing Guide](https://spring.io/guides/gs/testing-web/)
- [Vitest Documentation](https://vitest.dev/)
- [React Testing Library](https://testing-library.com/docs/react-testing-library/intro/)
- [JUnit 5 Guide](https://junit.org/junit5/docs/current/user-guide/)
