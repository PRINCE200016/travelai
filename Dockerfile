# ---- Build the backend
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
RUN mvn -q -B clean package -DskipTests

# ---- Run: API + static frontend from one container
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 tripmind
COPY --from=build /app/target/travel-planner-1.0.0.jar app.jar
COPY frontend /app/frontend
ENV PORT=7860 \
    FRONTEND_PATH=file:/app/frontend/ \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
USER tripmind
EXPOSE 7860
ENTRYPOINT ["java", "-jar", "app.jar"]
