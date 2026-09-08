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
COPY frontend-react/tailwind.config.js frontend-react/postcss.config.js frontend-react/index.html frontend-react/

# Build project
RUN chmod +x ./gradlew && ./gradlew build -x test

# Runtime stage
FROM eclipse-temurin:25.0.3_9-jre

WORKDIR /app

EXPOSE 8080

RUN mkdir -p /app/uploads && chmod 777 /app/uploads

COPY --from=builder /app/backend-springboot/build/libs/backend-springboot-0.0.1-SNAPSHOT.jar app.jar

ENV APP_STORAGE_ROOT=/app/uploads

ENTRYPOINT ["java", "-jar", "app.jar"]