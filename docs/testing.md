# Development and testing

## Package and run

Use Java 17 or newer. From the repository root, the Maven Wrapper runs the backend tests and packages the browser assets from `frontend/src` into the JAR.

```bash
sh ./mvnw -B clean verify
java -jar target/dormitory-management-system-1.1.0.jar --spring.profiles.active=demo
```

On Windows, use `.\mvnw.cmd -B clean verify` in PowerShell. Open the demo at [127.0.0.1 on port 8080](http://127.0.0.1:8080). After changing frontend files, rebuild and restart to see the changes. The demo seeds fictional data in a fresh H2 database at startup.

`verify` also checks:

- **Coverage.** JaCoCo writes a report to `target/site/jacoco/index.html`. The build fails if line coverage drops below 90% or branch coverage below 70%. The minimums are the `coverage.line.minimum` and `coverage.branch.minimum` properties in `pom.xml`.
- **API contract.** [`OpenApiContractTest`](../src/test/java/io/github/cpg25ncu/dormitory/OpenApiContractTest.java) compares every `/api` endpoint in the application with the [OpenAPI contract](openapi.yaml). It fails when an endpoint is missing from the contract, or when the contract lists an endpoint that does not exist.

## Browser workflow

After packaging, install the test dependencies with Node.js 24 and npm. Run these commands from the repository root.

```bash
cd frontend
npm ci
npx playwright install chromium
npm test
```

Playwright finds the packaged JAR in `target/`, starts it with its H2 demo profile on port `18080`, and shuts it down after the run. The server runs in the `Pacific/Kiritimati` time zone (UTC+14), so for most of the day the residence's date differs from the UTC date and from the browser's date. Tests that depend on "today" fail if any code falls back to UTC. The suite covers the administrator and staff workflow from check-in through absence recording, transfer and checkout. It also checks form validation, full rooms and duplicate check-ins, out-of-scope staff requests, and preservation of current assignments, occupancy and history after rejected requests. Failed runs write traces to `frontend/test-results/`.

Set `DORM_TEST_BROWSER_CHANNEL` to `chrome` or `msedge` before running `npm test` to use a locally installed browser.

### README screenshots

`npm run screenshots` in `frontend/` starts the packaged application on port `18081` and captures the images in [docs/media](media/) at 1440×960 with fictional demo records. Run it after interface changes so the README matches the application.

## Standalone browser demo

Run `npm --prefix frontend run build:demo` from the repository root. The command copies the application UI into `frontend/demo/dist/` and supplies a browser-only transport that uses fictional fixtures and tab-local storage. Serve that directory over HTTP to test the same static build used for the public demo. The two preset roles cover room assignment, transfers, check-out and absence recording. The demo uses the viewer's time zone.

The build also writes an API reference to `api/`, which renders [openapi.yaml](openapi.yaml) with Redoc. The [demo workflow](../.github/workflows/demo.yml) builds the same directory and publishes it to GitHub Pages when the frontend or the contract changes on `main`.

## Repository checks

[CodeQL](../.github/workflows/codeql.yml) scans the Java, JavaScript and workflow code on every pull request, on `main` and weekly. Results appear under the repository's Security tab. [Dependabot](../.github/dependabot.yml) proposes Maven, npm and GitHub Actions updates weekly, grouping minor and patch releases. Each update runs the same checks as any other pull request.

## MySQL integration

The integration suite runs on H2 by default. To run the same suite against MySQL 8.4, start Docker and add one property:

```bash
sh ./mvnw -B clean verify -Ddormitory.test.database=mysql
```

[Testcontainers](https://testcontainers.com/) starts a disposable MySQL 8.4 container for the run. It uses the same UTC session settings as a deployment, and Flyway migrates the container from an empty schema. No database account or connection settings are needed.

## What the suite covers

The HTTP integration suite checks sign-in, CSRF, building scope, session revocation, concurrent booking of the last bed, transfer rollback and checkout history. It also covers:

- the public endpoints and the residence time zone
- the allocation rules, including a policy change racing a check-in
- absence corrections and withdrawals
- paged search, filters and CSV exports
- sign-in throttling
- password changes, staff account validation, and the rules for editing and deleting rooms, buildings and students

A unit test covers CSV quoting and formula neutralization. The suite replaces the application clock with one that a test can pin to an instant, so it checks the hours when the local date and the UTC date differ and that timestamps are stored in UTC.

## GitHub Actions

| Check | What it verifies |
| --- | --- |
| Java 17 and H2 | Clean Maven build, integration tests, the coverage gate and the API contract test. Publishes the tested JAR and the coverage report as workflow artifacts and adds a coverage table to the run summary |
| MySQL 8.4 integration | The same tests against MySQL 8.4 in a Testcontainers container |
| Browser workflow | Both roles, validation messages and rejected writes in the UI served by the verified JAR |
| Docker Compose deployment | The image builds from source, both containers pass their health checks, and the [smoke test](deployment.md#health-checks-and-verification) signs in and runs check-in, absence and checkout against a fresh MySQL volume in a non-UTC time zone |
| Publish browser demo and API reference | Builds the standalone demo and the Redoc API reference and deploys them to GitHub Pages |
| CodeQL | Security analysis of Java, JavaScript and GitHub Actions workflows |
| Release | On a `v*.*.*` tag, it checks the tag against the project version and runs the full build. It then publishes the container image to GHCR and creates the GitHub release with the JAR, checksums and changelog section |

See the [build workflow](../.github/workflows/ci.yml), [demo workflow](../.github/workflows/demo.yml) and [deployment guide](deployment.md).
