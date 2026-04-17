# ===== Build stage =====
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn package -DskipTests -q

# ===== Runtime stage (minimal, secure) =====
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Create non-root user (security hardening)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy jar from builder
COPY --from=builder /app/target/trade-service-*.jar app.jar

# Set ownership
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

# JVM tuning for containers
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=production", \
  "-jar", "app.jar"]
