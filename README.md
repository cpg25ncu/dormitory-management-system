# Dormitory Management System

[![Build and verify](https://github.com/cpg25ncu/dormitory-management-system/actions/workflows/ci.yml/badge.svg)](https://github.com/cpg25ncu/dormitory-management-system/actions/workflows/ci.yml)

Dormitory Manager is a web application for university residence operations. It manages buildings, room capacity, student assignments and absence records through separate administrator and staff workspaces.

## Live Demo

[Open the interactive browser demo](https://cpg25ncu-dormitory-demo.notablecake16.chatgpt.site)

Choose **Use administrator** to check Taylor Nguyen into Cedar Hall. Sign out and choose **Use staff** to record an absence, then switch back to the administrator to transfer the student or check them out. The demo uses fictional records and stores changes in your browser tab. **Reset demo** restores the starting records.

![Administrator overview showing available beds, occupancy by building and recent absences](docs/media/overview.png)

## Features

| System administrator | Dormitory staff |
| --- | --- |
| Manage buildings, rooms, student records and staff accounts | View buildings, rooms and residents within their assignments |
| Assign staff to buildings and manage account access | Record resident absences and review their buildings’ absence history |
| Check students in, transfer rooms and check students out | Review live room occupancy and availability |
| Review all absence records and housing history | Change their own password |

## Engineering Highlights

- **Concurrent bed allocation.** Check-in locks the student and room rows with `SELECT … FOR UPDATE` and counts active stays inside the transaction. When two requests compete for the last bed, the integration test verifies one succeeds and the other returns HTTP 409. [Housing service](src/main/java/io/github/cpg25ncu/dormitory/housing/Housing.java) · [Integration tests](src/test/java/io/github/cpg25ncu/dormitory/ResidenceWorkflowsTest.java)
- **Transaction consistency.** Transfers acquire room locks in ascending order, close the existing stay, and open the destination stay in one transaction. Tests verify a failed transfer preserves the original assignment and a checkout releases its bed while keeping the recorded history. [Transaction design](docs/architecture.md#check-in-transfers-and-checkout)
- **Role-based access control.** Spring Security reserves administration endpoints for administrators. Database queries restrict staff to assigned buildings, including which residents they can record absences for. Disabling an account or changing its credentials invalidates its existing sessions. [Access rules](docs/architecture.md#request-boundaries)
- **Automated verification.** GitHub Actions runs the backend workflow suite against H2 and MySQL 8.4, then uses the verified JAR for the Playwright cross-role workflow and the Compose startup check. [Testing and CI](docs/testing.md)

## Screenshots

Captured from the Spring Boot application with fictional demo records. Select an image for the full-size view.

| Sign-in page | Student housing and check-in |
| --- | --- |
| [![Sign-in page with the administrator and staff demo options](docs/media/sign-in.png)](docs/media/sign-in.png) | [![Student housing table showing assignments, statuses, and check-in actions](docs/media/student-housing.png)](docs/media/student-housing.png) |
| **Room transfer** | **Staff absence log** |
| [![Room transfer dialog with a destination room selected](docs/media/room-transfer.png)](docs/media/room-transfer.png) | [![Staff absence log scoped to Cedar Hall](docs/media/staff-absences.png)](docs/media/staff-absences.png) |

## Try the local demo

Use Java 17 or newer. The Maven Wrapper downloads the project's Maven version on its first run.

```bash
sh ./mvnw -B clean verify
java -jar target/dormitory-management-system-1.0.0.jar --spring.profiles.active=demo
```

On Windows, run `.\mvnw.cmd -B clean verify` in PowerShell in place of the first command.

Open [Dormitory Manager on localhost](http://127.0.0.1:8080). The sign-in page offers two prefilled demo accounts. Their usernames are `admin` and `staff`. The staff workspace is assigned to Cedar Hall. Demo data is fictional and lives in an in-memory database for the duration of the process.

Try adding a student as an administrator, assigning a Cedar Hall room, then recording an absence for that resident as staff. Sign back in as the administrator to transfer or check out the student and check the occupancy and history views.

## Run with persistent storage

Copy `.env.example` to `.env` and set distinct values for `DORM_DB_PASSWORD` and `DORM_ADMIN_PASSWORD`. Build the application JAR, then start the application and MySQL 8.4 with Docker Compose.

```bash
sh ./mvnw -B clean verify
docker compose up --build -d
docker compose logs -f app
```

Sign in at [http://127.0.0.1:8080](http://127.0.0.1:8080) with the configured administrator username and password. Flyway applies the schema at startup. The MySQL volume persists data between restarts, and the bootstrap password is used when creating the first administrator. See [Deployment and operations](docs/deployment.md) for configuration and backups.

## Project structure

| Path | Purpose |
| --- | --- |
| [frontend/src/](frontend/src/) | HTML, CSS and native JavaScript modules for both workspaces |
| [frontend/tests/](frontend/tests/) | Browser tests against the packaged application |
| [src/main/java/](src/main/java/) | Spring Boot API, Spring Security and business logic |
| [src/main/resources/](src/main/resources/) | Spring profiles and versioned Flyway migrations |
| [src/test/](src/test/) | Spring MVC and database integration tests |
| [compose.yaml](compose.yaml) | Application and MySQL deployment |
| [.github/workflows/](.github/workflows/) | Build, MySQL, browser and Compose checks |

Maven packages the frontend with Spring Boot in one JAR. The browser loads the UI and API on the same origin. Node.js supports the Playwright test suite and the [standalone browser demo build](docs/testing.md#standalone-browser-demo).

## Development and contributors

- [Architecture and roles](docs/architecture.md)
- [OpenAPI contract](docs/openapi.yaml)
- [Testing and CI](docs/testing.md)
- [Deployment and backups](docs/deployment.md)
- [Contribution guide](.github/CONTRIBUTING.md)
