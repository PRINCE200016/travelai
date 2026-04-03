# Stage 1: Build the backend
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
# Copy the backend source
COPY backend/pom.xml .
COPY backend/src ./src
# Build the application (skipping tests for speed)
RUN mvn clean package -DskipTests

# Stage 2: Run the backend and serve frontend mapping
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the built jar
COPY --from=build /app/target/travel-planner-1.0.0.jar app.jar

# IMPORTANT: Copy the frontend code to explicitly map the directory
# The WebConfig is configured to map "file:../frontend/" which resolves against the working directory
COPY frontend /frontend

# Expose the API port
EXPOSE 8080

# Environment variables will be injected by docker-compose/platform
ENTRYPOINT ["java", "-jar", "app.jar"]
