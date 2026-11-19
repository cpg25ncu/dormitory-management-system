# Dormitory Management System

[![Build and verify](https://github.com/cpg25ncu/dormitory-management-system/actions/workflows/ci.yml/badge.svg)](https://github.com/cpg25ncu/dormitory-management-system/actions/workflows/ci.yml)

Dormitory Manager is a web application for university residence operations. It manages buildings, room capacity, student assignments and absence records through separate administrator and staff workspaces.

![Administrator overview showing available beds, occupancy by building and recent absences](docs/media/overview.png)

## Features

| System administrator | Dormitory staff |
| --- | --- |
| Manage buildings, rooms, student records and staff accounts | View buildings, rooms and residents within their assignments |
| Assign staff to buildings and manage account access | Record resident absences and review their buildings’ absence history |
| Check students in, transfer rooms and check students out | Review live room occupancy and availability |
| Review all absence records and housing history | Change their own password |

Occupancy comes from active room assignments. A check-in or transfer reserves a bed within a database transaction, and closing an assignment records who processed it. Database constraints protect linked records and prevent multiple active assignments for the same student.

## Try the local demo

Requirements are Java 17 or newer and Maven 3.9 or newer.

```bash
mvn -B verify
java -jar target/dormitory-management-system-1.0.0.jar --spring.profiles.active=demo
```

Open [Dormitory Manager on localhost](http://127.0.0.1:8080). The sign-in page offers two prefilled demo accounts. Their usernames are `admin` and `staff`. The staff workspace is assigned to Cedar Hall. Demo data is fictional and lives in an in-memory database for the duration of the process.

Try adding a student as an administrator, assigning a Cedar Hall room, then recording an absence for that resident as staff. Sign back in as the administrator to transfer or check out the student and check the occupancy and history views.

## Run with persistent storage

Copy `.env.example` to `.env` and set distinct values for `DORM_DB_PASSWORD` and `DORM_ADMIN_PASSWORD`. Start the application and MySQL 8.4 with Docker Compose.

```bash
docker compose up --build -d
docker compose logs -f app
```

Sign in at [http://127.0.0.1:8080](http://127.0.0.1:8080) with the configured administrator username and password. Flyway applies the schema at startup. The MySQL volume persists data between restarts, and the bootstrap password is used when creating the first administrator. See [Deployment and operations](docs/deployment.md) for configuration and backups.

## Project structure

```text
src/main/java/.../dormitory/
    account/        Sign-in identity, credentials and staff administration
    attendance/     Building-scoped absence records
    bootstrap/      First administrator and local demo fixtures
    common/         API error responses
    directory/      Building and student records
    housing/        Rooms, assignments, capacity and housing history
    security/       Session validity, roles, request protection and headers
src/main/resources/
    db/migration/   Versioned database schema
    static/         Browser interface
src/test/           End-to-end application workflows through HTTP controllers
docs/               Architecture and deployment
.github/workflows/  Java build and MySQL integration checks
```

The application uses Java, Spring Boot MVC, Spring JDBC, Spring Security, Flyway and MySQL. The browser interface uses HTML, CSS and native JavaScript modules. Read [Architecture](docs/architecture.md) for the data model, access rules and transaction design.

`mvn verify` checks sign-in, session invalidation, request protection, building scope, concurrent requests for the last available bed, transfers and checkout. CI runs the suite against both H2 and MySQL 8.4 and verifies first sign-in in a clean Docker Compose deployment.
