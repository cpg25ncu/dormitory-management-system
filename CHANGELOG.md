# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-11-20

### Added

- Building gender policies (mixed, female only, male only) and single-gender rooms. Check-ins and transfers require a recorded gender and a room that fits it, and edits that would break the rules are rejected. ([#2](https://github.com/cpg25ncu/dormitory-management-system/pull/2))
- Student gender and optional phone and email. ([#2](https://github.com/cpg25ncu/dormitory-management-system/pull/2))
- Correcting and withdrawing absence records. The record keeps who made the change, when and why, and recording the same day again restores a withdrawn record. ([#3](https://github.com/cpg25ncu/dormitory-management-system/pull/3))
- Server-side paging, search and filters for students, absence records and housing history, with CSV exports for each. ([#4](https://github.com/cpg25ncu/dormitory-management-system/pull/4))
- `DORM_TIME_ZONE` sets the residence's time zone for calendar days and displayed times. ([#1](https://github.com/cpg25ncu/dormitory-management-system/pull/1))
- Public health, liveness and readiness probes, a container health check, and `scripts/smoke-test.mjs` for verifying a deployment. ([#1](https://github.com/cpg25ncu/dormitory-management-system/pull/1))
- A browser demo and a browsable API reference on GitHub Pages. ([#1](https://github.com/cpg25ncu/dormitory-management-system/pull/1), [#7](https://github.com/cpg25ncu/dormitory-management-system/pull/7))
- A container image on the GitHub Container Registry and a JAR with checksums for each release.
- A coverage gate (90% of lines, 70% of branches), an OpenAPI contract test, and `-Ddormitory.test.database=mysql` to run the suite on MySQL 8.4 with Testcontainers. ([#6](https://github.com/cpg25ncu/dormitory-management-system/pull/6))

### Changed

- **Breaking:** `GET /api/admin/students`, `GET /api/absences` and `GET /api/admin/housing/history` return a page (`items`, `page`, `size`, `totalItems`, `totalPages`) instead of an array. ([#4](https://github.com/cpg25ncu/dormitory-management-system/pull/4))
- **Breaking:** `gender` is required when creating or updating a student. ([#2](https://github.com/cpg25ncu/dormitory-management-system/pull/2))
- `GET /api/absences` returns active records unless `status` is given. ([#3](https://github.com/cpg25ncu/dormitory-management-system/pull/3))
- The container image builds from source in a multi-stage Dockerfile and runs the layered application as an unprivileged user. ([#1](https://github.com/cpg25ncu/dormitory-management-system/pull/1))
- The demo profile serves its sign-ins through `GET /api/session`, so the frontend no longer contains demo passwords. ([#1](https://github.com/cpg25ncu/dormitory-management-system/pull/1))
- Spring Boot 4.1.2, Maven 3.10.1 and current GitHub Actions. ([#8](https://github.com/cpg25ncu/dormitory-management-system/pull/8), [#9](https://github.com/cpg25ncu/dormitory-management-system/pull/9))

### Fixed

- Absence dates used the UTC day, so a residence ahead of UTC could not record the current day's absences until the UTC date changed. ([#1](https://github.com/cpg25ncu/dormitory-management-system/pull/1))

### Security

- Five failed sign-ins for one username from one address within 15 minutes lock that pair for 15 minutes. ([#5](https://github.com/cpg25ncu/dormitory-management-system/pull/5), [#10](https://github.com/cpg25ncu/dormitory-management-system/pull/10))
- CSV exports neutralize cells that a spreadsheet would run as formulas. ([#4](https://github.com/cpg25ncu/dormitory-management-system/pull/4))
- CodeQL analysis, Dependabot updates, a security policy with private vulnerability reporting, and secret scanning with push protection. ([#7](https://github.com/cpg25ncu/dormitory-management-system/pull/7), [#10](https://github.com/cpg25ncu/dormitory-management-system/pull/10))

### Upgrading from 1.0.0

- Flyway applies migrations V2 to V4 at startup. Existing buildings become mixed. Existing students keep their stays but need a recorded gender before their next check-in or transfer.
- Set `DORM_TIME_ZONE` to the residence's time zone. Behind a trusted reverse proxy, also set `DORM_FORWARD_HEADERS=native` so sign-in throttling sees each client's address.
- API clients must read the paged list responses and send `gender` for students. See the [OpenAPI contract](docs/openapi.yaml).

## [1.0.0] - 2026-11-19

### Added

- Buildings, rooms, students and staff accounts, with separate administrator and staff workspaces.
- Check-in, room transfers and checkout. Row locks prevent two students from taking the last bed, and every stay keeps its housing history.
- Absence records scoped to the buildings assigned to each staff account.
- Session sign-in with CSRF protection and BCrypt passwords. Disabling an account or changing its password ends its sessions.
- Flyway migrations, MySQL 8.4 deployment with Docker Compose, and CI against H2, MySQL and the browser.

[1.1.0]: https://github.com/cpg25ncu/dormitory-management-system/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/cpg25ncu/dormitory-management-system/releases/tag/v1.0.0
