# syntax=docker/dockerfile:1
# ----------------------------------------------------------------
# Stage 1: Build Java Application
# ----------------------------------------------------------------
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /build

# Install Maven
RUN apk add --no-cache maven

# Copy POM and dependencies definition
COPY pom.xml .
COPY docs ./docs

# Download dependencies in a cached layer
RUN mvn -B dependency:go-offline || true

# Copy source code and build production jar (excluding tests during image packaging)
COPY src ./src
RUN mvn -B clean package -DskipTests

# ----------------------------------------------------------------
# Stage 2: Minimal Production JRE Image
# ----------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runner

WORKDIR /app

# Install curl for Actuator health checks and create unprivileged user
RUN apk add --no-cache curl && \
    addgroup -g 10001 appgroup && \
    adduser -u 10001 -G appgroup -s /bin/sh -D appuser

# Copy executable jar from builder
COPY --from=builder --chown=appuser:appgroup /build/target/invest-tracker-*.jar /app/app.jar

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=5 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
