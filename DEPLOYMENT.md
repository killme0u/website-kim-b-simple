# 배포 준비 체크리스트

## 📋 전제 조건

### 개발 완료
- [x] 모든 기능 구현 완료
- [x] 단위 테스트 작성 완료 (37+ 테스트 케이스)
- [x] E2E 테스트 스펙 작성 완료
- [x] 코드 리뷰 완료

### 배포 환경 준비
- [ ] Docker 이미지 빌드 테스트
- [ ] 데이터베이스 마이그레이션 검증
- [ ] 환경 변수 설정 검증
- [ ] SSL/TLS 인증서 준비

---

## 🔧 단계별 배포 준비

### Step 1: 테스트 실행

#### 1-1. 백엔드 테스트
```bash
cd backend-springboot
./gradlew test

# 기대 결과:
# - 모든 테스트 통과
# - 커버리지 > 80%
```

#### 1-2. 프론트엔드 테스트
```bash
cd frontend-react
npm install --save-dev \
  vitest \
  @testing-library/react \
  @testing-library/user-event

npm run test:run

# 기대 결과:
# - 모든 테스트 통과
# - 커버리지 > 90%
```

#### 1-3. E2E 테스트
```bash
cd frontend-react
npm install --save-dev @playwright/test

npx playwright install

npm run build  # 번들 생성 필수

npx playwright test

# 기대 결과:
# - must-change-password.spec.ts 통과
# - username-recovery.spec.ts 통과
# - 모든 브라우저에서 통과 (chromium, firefox, webkit)
```

---

### Step 2: 빌드 및 패키징

#### 2-1. Backend JAR 빌드
```bash
cd backend-springboot
./gradlew clean build -x test

# 출력 파일:
# - build/libs/board-backend-*.jar
# - build/libs/board-backend-*.war (if configured)
```

#### 2-2. Frontend 번들 빌드
```bash
cd frontend-react
npm run build

# 출력 디렉토리:
# - build/dist/
# - 포함: index.html, assets/*, favicon.svg
```

#### 2-3. Docker 이미지 빌드
```bash
docker build -t board-system:latest .

# 테스트:
docker run -p 8080:8080 board-system:latest

# 확인:
curl http://localhost:8080/api/boards
```

---

### Step 3: 데이터베이스 마이그레이션

#### 3-1. 마이그레이션 파일 확인
```bash
ls -la backend-springboot/src/main/resources/db/migration/

# 파일 목록:
# - V1__init.sql
# - V2__seed_board.sql
# - ...
# - V7__add_query_optimization_indexes.sql
```

#### 3-2. 마이그레이션 검증
```bash
# 프로덕션 데이터베이스에 대해:
# 1. 백업 생성
pg_dump board_db > backup_$(date +%s).sql

# 2. 마이그레이션 실행 (Spring Boot 자동 실행)
# 또는 수동 실행:
psql -h <host> -U board_user -d board_db < backend-springboot/src/main/resources/db/migration/V7__*.sql

# 3. 인덱스 생성 확인
psql -h <host> -U board_user -d board_db -c "\di"
```

#### 3-3. 기대 인덱스
```sql
- idx_member_email
- idx_board_slug
- idx_post_member
- idx_comment_member
- idx_attachment_stored_name
- idx_view_log_member
- idx_post_like_member
```

---

### Step 4: 환경 변수 설정

#### 4-1. 프로덕션 .env 파일
```bash
# Database
PGSQL_HOST=<production-db-host>
PGSQL_PORT=5432

# Mail (SMTP)
MAIL_SMTP_HOST=<smtp-host>
MAIL_SMTP_PORT=465
MAIL_SMTP_SSL=true
MAIL_SMTP_STARTTLS=false
MAIL_USERNAME=<email>
MAIL_PASSWORD=<app-password>
MAIL_FROM_ADMIN=<sender-email>
MAIL_DEBUG=false

# Application
APP_BASE_URL=https://yourdomain.com

# CAPTCHA (Production: 실제 Turnstile 키 사용)
CAPTCHA_MODE=remote
CAPTCHA_ENDPOINT=https://challenges.cloudflare.com/turnstile/v0/siteverify
CAPTCHA_SECRET=<production-secret>
```

#### 4-2. Spring Boot application.yml
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${PGSQL_HOST}:${PGSQL_PORT}/board_db
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate  # Production: validate 사용
  mail:
    host: ${MAIL_SMTP_HOST}
    port: ${MAIL_SMTP_PORT}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    
server:
  port: 8080
  compression:
    enabled: true
    min-response-size: 1024
```

#### 4-3. Frontend 환경 변수
```bash
# frontend-react/.env.production
VITE_API_BASE_URL=https://yourdomain.com/api
VITE_CAPTCHA_SITE_KEY=<cloudflare-site-key>
```

---

### Step 5: 보안 검증

#### 5-1. HTTPS/SSL 설정
```bash
# 인증서 생성 (Let's Encrypt)
certbot certonly --standalone -d yourdomain.com

# Spring Boot에 설정
server:
  ssl:
    key-store: /etc/ssl/certs/keystore.p12
    key-store-password: ${SSL_PASSWORD}
    key-store-type: PKCS12
    key-alias: tomcat
  http2:
    enabled: true
```

#### 5-2. CORS 설정
```yaml
# Spring Security
spring:
  web:
    cors:
      allowed-origins: https://yourdomain.com
      allowed-methods: GET,POST,PUT,DELETE
      allowed-headers: '*'
      max-age: 3600
```

#### 5-3. 보안 헤더
```yaml
# Spring Security에 추가
server:
  servlet:
    session:
      http-only: true
      secure: true
      same-site: strict
```

---

### Step 6: 성능 최적화 확인

#### 6-1. 캐싱 설정
```yaml
spring:
  cache:
    type: simple  # 또는 redis
    cache-names: boards,board
```

#### 6-2. 비동기 설정
```yaml
spring:
  task:
    execution:
      pool:
        core-size: 4
        max-size: 8
        queue-capacity: 100
    scheduling:
      pool:
        size: 2
```

---

### Step 7: 모니터링 및 로깅

#### 7-1. 로그 설정
```yaml
logging:
  level:
    root: INFO
    page.sanotehu.board.backend: DEBUG
  file:
    name: /var/log/board-app/application.log
    max-size: 10MB
    max-history: 10
```

#### 7-2. 메트릭 수집
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

---

### Step 8: 배포 실행

#### 8-1. Docker Compose 배포
```bash
docker-compose -f docker-compose.prod.yml up -d

# 상태 확인
docker-compose ps
docker logs board-app
```

#### 8-2. Kubernetes 배포 (선택)
```bash
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl get pods
kubectl logs -f deployment/board-app
```

---

## ✅ 배포 후 검증

### 9-1. 헬스 체크
```bash
curl -s http://localhost:8080/actuator/health | jq .

# 기대 응답:
# {
#   "status": "UP",
#   "components": {
#     "db": { "status": "UP" },
#     "mail": { "status": "UP" }
#   }
# }
```

### 9-2. API 테스트
```bash
# 게시판 목록 조회
curl -s http://localhost:8080/api/boards | jq .

# 캐싱 확인
time curl -s http://localhost:8080/api/boards > /dev/null
# 첫 번째: ~100ms
# 두 번째: ~10ms (캐시)
```

### 9-3. 기능 테스트
```bash
# 임시 비밀번호 발급
curl -X POST http://localhost:8080/api/members/password-reset/issue-temp-password \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","captchaToken":"dev-captcha"}'

# 아이디 찾기
curl -X POST http://localhost:8080/api/members/username-recovery \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","captchaToken":"dev-captcha"}'
```

---

## 📊 배포 체크리스트

### 배포 전
- [ ] 모든 테스트 통과 (유닛 + E2E)
- [ ] 코드 리뷰 승인
- [ ] 보안 취약점 스캔 완료
- [ ] 성능 테스트 통과
- [ ] 데이터베이스 백업 생성

### 배포 중
- [ ] Docker 이미지 빌드 성공
- [ ] 데이터베이스 마이그레이션 성공
- [ ] 환경 변수 설정 확인
- [ ] SSL/TLS 인증서 설치
- [ ] 서비스 헬스 체크 통과

### 배포 후
- [ ] API 응답 확인
- [ ] 캐싱 동작 확인
- [ ] 메일 발송 테스트
- [ ] 로그 수집 확인
- [ ] 모니터링 대시보드 확인

---

## 🚨 롤백 계획

배포 후 문제 발생 시:

```bash
# 1. 이전 Docker 이미지로 롤백
docker pull board-system:previous
docker-compose down
docker-compose -f docker-compose.prod.yml up -d

# 2. 데이터베이스 롤백 (필요한 경우)
psql -h <host> -U board_user -d board_db < backup_*.sql

# 3. 헬스 체크 재실행
curl -s http://localhost:8080/actuator/health
```

---

## 📞 배포 후 지원

- 성능 모니터링: Prometheus + Grafana
- 에러 추적: Sentry 또는 Rollbar
- 로그 분석: ELK Stack 또는 Datadog
- 알림: PagerDuty 또는 Slack

---

## 예상 배포 시간

| 단계 | 예상 시간 |
|------|---------|
| 테스트 | 10분 |
| 빌드 | 5분 |
| DB 마이그레이션 | 2분 |
| Docker 배포 | 5분 |
| 검증 | 5분 |
| **총합** | **27분** |

---

## 배포 완료 신호

```
✅ 모든 테스트 통과
✅ 헬스 체크 성공
✅ API 응답 정상
✅ 캐싱 동작 확인
✅ 메일 발송 성공
✅ 모니터링 대시보드 활성화

🚀 프로덕션 배포 완료!
```
