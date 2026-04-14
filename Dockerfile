# Stage 1: Build the backend
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
# Copy the backend source
COPY pom.xml .
COPY src ./src
# Build the application (skipping tests for speed)
RUN mvn clean package -DskipTests

# Stage 2: Run the backend
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the built jar
COPY --from=build /app/target/travel-planner-1.0.0.jar app.jar

# Expose the Hugging Face standard port
EXPOSE 7860

# Force the server to listen on port 7860
ENV PORT=7860

ENTRYPOINT ["java", "-jar", "app.jar"]
