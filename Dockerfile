FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN addgroup --system dormitory && adduser --system --ingroup dormitory dormitory
WORKDIR /opt/dormitory
COPY --from=build /build/target/dormitory-management-system-*.jar ./app.jar
USER dormitory
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/dormitory/app.jar"]
