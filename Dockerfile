# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper và pom trước (layer cache: chỉ re-download deps khi pom thay đổi)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

# Copy source và build
COPY src ./src
RUN ./mvnw package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# wget dùng cho HEALTHCHECK (alpine không có sẵn curl)
RUN apk add --no-cache wget

# Non-root user cho bảo mật
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Chỉ copy fat JAR từ builder — Flyway migrations đã embedded trong JAR
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# Docker theo dõi health của app — depends_on: condition: service_healthy sẽ đợi cái này pass
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]