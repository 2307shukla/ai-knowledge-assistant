# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Dependencies first: this layer is cached until pom.xml changes.
# Keeps rebuilds fast instead of re-downloading Maven + all deps every time.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Source last: only this layer re-runs when code changes.
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/ai-knowledge-assistant-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
