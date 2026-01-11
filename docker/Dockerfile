# Multi-stage build for Flow-Forge DAG Workflow Engine
# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS builder

LABEL maintainer="Flow-Forge Team <support@workflow.com>"
LABEL description="Flow-Forge DAG Workflow Engine"

# Set working directory
WORKDIR /build

# Copy pom files (for better layer caching)
COPY pom.xml .
COPY flow-forge-core-model/pom.xml flow-forge-core-model/
COPY flow-forge-nodes/pom.xml flow-forge-nodes/
COPY flow-forge-infrastructure/pom.xml flow-forge-infrastructure/
COPY flow-forge-engine/pom.xml flow-forge-engine/
COPY flow-forge-trigger/pom.xml flow-forge-trigger/
COPY flow-forge-visualizer/pom.xml flow-forge-visualizer/
COPY flow-forge-api/pom.xml flow-forge-api/
COPY flow-forge-deployment/pom.xml flow-forge-deployment/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY flow-forge-core-model/src flow-forge-core-model/src
COPY flow-forge-nodes/src flow-forge-nodes/src
COPY flow-forge-infrastructure/src flow-forge-infrastructure/src
COPY flow-forge-engine/src flow-forge-engine/src
COPY flow-forge-trigger/src flow-forge-trigger/src
COPY flow-forge-visualizer/src flow-forge-visualizer/src
COPY flow-forge-api/src flow-forge-api/src

# Build application
RUN mvn clean package -DskipTests -B && \
    mv flow-forge-api/target/*.jar app.jar

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Flow-Forge Team <support@workflow.com>"

# Install required packages
RUN apk add --no-cache \
    curl \
    tzdata \
    && rm -rf /var/cache/apk/*

# Create non-root user
RUN addgroup -S flowforge && adduser -S flowforge -G flowforge

# Set timezone
ENV TZ=Asia/Shanghai

# Create directories
RUN mkdir -p /var/log/flow-forge /app/logs && \
    chown -R flowforge:flowforge /var/log/flow-forge /app

# Set working directory
WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder /build/app.jar /app/flow-forge.jar

# Change to non-root user
USER flowforge

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Set JVM options - Optimized for high concurrency & low latency with Virtual Threads
# Using ZGC (Java 21+) for sub-millisecond GC pauses
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:MinRAMPercentage=50.0 \
               \
               -XX:+UseZGC \
               -XX:+ZGenerational \
               -XX:ConcGCThreads=2 \
               -XX:ParallelGCThreads=4 \
               \
               -XX:+AlwaysPreTouch \
               -XX:+UseStringDeduplication \
               \
               -Xss256k \
               -Djdk.virtualThreadScheduler.parallelism=8 \
               -Djdk.virtualThreadScheduler.maxPoolSize=256 \
               \
               -XX:CompileThreshold=10000 \
               -XX:+TieredCompilation \
               -XX:TieredStopAtLevel=4 \
               \
               -XX:+UnlockDiagnosticVMOptions \
               -XX:+LogVMOutput \
               -XX:LogFile=/app/logs/jvm.log \
               \
               -Xlog:gc*:file=/app/logs/gc.log:time,level,tags:filecount=5,filesize=50m \
               -Xlog:safepoint:file=/app/logs/safepoint.log:time,level,tags:filecount=3,filesize=10m \
               \
               -XX:+UseFastAccessorMethods \
               -XX:+UseInlineCaches \
               \
               -Djdk.nio.maxCachedBufferSize=262144 \
               -Dio.netty.allocator.type=pooled \
               -Dio.netty.leakDetection.level=disabled \
               \
               -Djava.awt.headless=true \
               -Dfile.encoding=UTF-8 \
               -Dsun.jnu.encoding=UTF-8"

# Set Spring profile
ENV SPRING_PROFILES_ACTIVE=prod

# Start application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=$SPRING_PROFILES_ACTIVE -jar /app/flow-forge.jar"]
