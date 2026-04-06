FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
# Cache dependencies
RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw package -DskipTests

# NEW: Extract layers from the fat JAR
RUN java -Djarmode=layertools -jar target/*.jar extract

FROM eclipse-temurin:25-jre
WORKDIR /app

# Install curl for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring

# 2. Performance: Copy layers individually
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./

# 3. Efficiency: JVM reads JAVA_TOOL_OPTIONS automatically
ENV JAVA_TOOL_OPTIONS="-XX:+UseParallelGC -XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=20.0"

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
