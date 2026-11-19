# syntax=docker/dockerfile:1

# Build the application JAR from source, then split it into layers so that
# dependency layers are reused when only application code changes.
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -q dependency:go-offline
COPY LICENSE ./
COPY src/main/ src/main/
COPY frontend/src/ frontend/src/
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/dormitory-management-system-*.jar \
        extract --layers --launcher --destination target/extracted

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system dormitory && useradd --system --gid dormitory --no-create-home dormitory
WORKDIR /opt/dormitory
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER dormitory
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
