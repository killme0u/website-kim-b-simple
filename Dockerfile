FROM eclipse-temurin:25.0.3_9-jdk AS builder

WORKDIR /app

# Copy gradle wrapper and config first to cache dependencies
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY backend-springboot/build.gradle backend-springboot/
COPY frontend-react/build.gradle frontend-react/
COPY frontend-react/package.json frontend-react/package-lock.json ./frontend-react/

# Copy the rest
COPY backend-springboot/src backend-springboot/src
COPY frontend-react/src frontend-react/src
COPY frontend-react/vite.config.ts frontend-react/
COPY frontend-react/tsconfig.json frontend-react/tsconfig.node.json frontend-react/tsconfig.app.json frontend-react/
# Tailwind v4 는 tailwind.config.js / postcss.config.js 를 쓰지 않는다(667047a 에서 삭제됨).
# 설정은 src/index.css 의 @import 'tailwindcss' 와 vite.config.ts 의 @tailwindcss/vite 플러그인에 있고,
# 둘 다 위에서 이미 복사한 경로 안에 들어 있다.
COPY frontend-react/index.html frontend-react/
# public/ 은 Vite 가 build/dist 로 그대로 복사하는 정적 자산이다. 빠뜨리면 index.html 이
# 참조하는 /favicon.svg 가 이미지 안에서 404 가 된다.
COPY frontend-react/public frontend-react/public

# Build project
RUN chmod +x ./gradlew && ./gradlew build -x test

# Runtime stage
FROM eclipse-temurin:25.0.3_9-jre

WORKDIR /app

EXPOSE 8080

RUN mkdir -p /app/uploads && chmod 777 /app/uploads

# backend-springboot/build.gradle 이 war 플러그인을 쓰므로 Spring Boot 는 bootJar 가 아니라
# bootWar 를 만든다. build/libs 에 남는 것은 아래 세 개이고, java -jar 로 실행 가능한 것은
# 확장자 앞에 -plain 이 없는 .war 하나뿐이다.
#   backend-springboot-0.0.1-SNAPSHOT.war         ← 실행 가능 (bootWar)
#   backend-springboot-0.0.1-SNAPSHOT-plain.war   ← 클래스만. 실행 불가
#   backend-springboot-0.0.1-SNAPSHOT-plain.jar   ← 클래스만. 실행 불가
COPY --from=builder /app/backend-springboot/build/libs/backend-springboot-0.0.1-SNAPSHOT.war app.war

ENV APP_STORAGE_ROOT=/app/uploads

ENTRYPOINT ["java", "-jar", "app.war"]