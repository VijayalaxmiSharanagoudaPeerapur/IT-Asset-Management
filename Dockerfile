# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy maven wrapper and pom.xml
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x ./mvnw

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B || true

# Copy source files
COPY src src

# Package application jar (skipping unit tests for fast image builds)
RUN ./mvnw clean package -DskipTests

# Stage 2: Production JRE runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create a non-root group and user for security
RUN groupadd -r itasm && useradd -r -g itasm itasm

# Copy compiled jar from build stage
COPY --from=build --chown=itasm:itasm /app/target/*.jar app.jar

USER itasm

# Expose Spring Boot default port
EXPOSE 8080

# Configure JVM memory limits for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
