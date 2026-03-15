FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
# Cache dependencies
RUN ./mvnw dependency:go-offline

COPY src src
RUN ./mvnw package -DskipTests

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

# 3. Efficiency: Enable Virtual Threads (Java 21+) and memory limits
ENV JAVA_OPTS="-XX:+UseParallelGC -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
