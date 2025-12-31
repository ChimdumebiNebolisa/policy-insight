# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (cached layer if pom.xml unchanged)
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application (skip tests)
RUN ./mvnw -DskipTests package

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy the JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose port (Cloud Run will set PORT env var)
EXPOSE 8080

# Cloud Run sets PORT env var, Spring Boot will read it via application-cloudrun.yml
# Default to 8080 if PORT is not set

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

