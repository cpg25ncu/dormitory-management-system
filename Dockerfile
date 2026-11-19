FROM eclipse-temurin:17-jre-jammy
RUN addgroup --system dormitory && adduser --system --ingroup dormitory dormitory
WORKDIR /opt/dormitory
COPY target/dormitory-management-system-*.jar ./app.jar
USER dormitory
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/dormitory/app.jar"]
