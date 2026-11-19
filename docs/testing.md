# Development and testing

## Package and run

Use Java 17 or newer. From the repository root, the Maven Wrapper runs the backend tests and packages the browser assets from `frontend/src` into the JAR.

```bash
sh ./mvnw -B clean verify
java -jar target/dormitory-management-system-1.0.0.jar --spring.profiles.active=demo
```

On Windows, use `.\mvnw.cmd -B clean verify` in PowerShell. Open the demo at [127.0.0.1 on port 8080](http://127.0.0.1:8080). After changing frontend files, rebuild and restart to see the changes. The demo seeds fictional data in a fresh H2 database at startup.

## Browser workflow

After packaging, install the test dependencies with Node.js 24 and npm. Run these commands from the repository root.

```bash
cd frontend
npm ci
npx playwright install chromium
npm test
```

Playwright starts the packaged Spring Boot JAR with its H2 demo profile on port `18080` and shuts it down after the run. The server runs in the `Pacific/Kiritimati` time zone (UTC+14), so for most of the day the residence's date differs from the UTC date and from the browser's date. Tests that depend on "today" fail if any code falls back to UTC. The suite covers the administrator and staff workflow from check-in through absence recording, transfer and checkout. It also checks form validation, full rooms and duplicate check-ins, out-of-scope staff requests, and preservation of current assignments, occupancy and history after rejected requests. Failed runs write traces to `frontend/test-results/`.

Set `DORM_TEST_BROWSER_CHANNEL` to `chrome` or `msedge` before running `npm test` to use a locally installed browser.

## Standalone browser demo

Run `npm --prefix frontend run build:demo` from the repository root. The command copies the application UI into `frontend/demo/dist/` and supplies a browser-only transport that uses fictional fixtures and tab-local storage. Serve that directory over HTTP to test the same static build used for the public demo. The two preset roles cover room assignment, transfers, check-out and absence recording. The demo uses the viewer's time zone.

The [demo workflow](../.github/workflows/demo.yml) builds the same directory and publishes it to GitHub Pages when the frontend changes on `main`.

## MySQL integration

Run the Maven verification command with these variables pointed at a dedicated empty MySQL 8.4 database. The suite runs Flyway and writes demo and test fixtures.

| Variable | Example |
| --- | --- |
| `DORM_TEST_DB_URL` | `jdbc:mysql://127.0.0.1:3306/dormitory_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` |
| `DORM_TEST_DB_USERNAME` | `dormitory_test` |
| `DORM_TEST_DB_PASSWORD` | Password for that test database account |

The HTTP integration suite checks sign-in, CSRF, building scope, session revocation, concurrent booking of the last bed, transfer rollback and checkout history. It also covers the public endpoints and the residence time zone. The suite replaces the application clock with one that a test can pin to an instant, so it checks the hours when the local date and the UTC date differ and that timestamps are stored in UTC.

## GitHub Actions

| Check | What it verifies |
| --- | --- |
| Java 17 and H2 | Clean Maven build, integration tests and publication of the tested JAR as a workflow artifact |
| MySQL 8.4 integration | The same tests against the database used for deployment |
| Browser workflow | Both roles, validation messages and rejected writes in the UI served by the verified JAR |
| Docker Compose deployment | The image builds from source, both containers pass their health checks, and the [smoke test](deployment.md#health-checks-and-verification) signs in and runs check-in, absence and checkout against a fresh MySQL volume in a non-UTC time zone |
| Publish browser demo | Builds the standalone demo and deploys it to GitHub Pages |

See the [build workflow](../.github/workflows/ci.yml), [demo workflow](../.github/workflows/demo.yml) and [deployment guide](deployment.md).
