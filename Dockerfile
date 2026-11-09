# Build stage: compiles the app; none of this ends up in the final image
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Dependencies first, so this slow layer is reused until pom.xml changes
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests

# Runtime stage: JRE and the jar only
FROM eclipse-temurin:25-jre
WORKDIR /app

# Run as a non-root user that can only write to the uploads folder
RUN useradd --system --no-create-home app && mkdir uploads && chown app uploads
COPY --from=build /build/target/*.jar app.jar
USER app

ENV STORAGE_DIR=/app/uploads
EXPOSE 8080

# MaxRAMPercentage sizes the heap from the container's memory limit (Render's free plan is small)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
