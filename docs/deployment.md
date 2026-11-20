# Deployment and operations

## Start or update a deployment

Docker Compose builds the application image from source and starts it with MySQL 8.4. No local JDK is needed. From the repository root, copy `.env.example` to `.env`, set distinct passwords for MySQL and the initial administrator, and set `DORM_TIME_ZONE` to the residence's time zone.

```bash
cp .env.example .env
docker compose config --quiet
docker compose up --build -d --wait
docker compose logs -f app
```

The image build runs Maven in a builder stage and copies the application into a JRE image in Spring Boot layers, so rebuilding after a code change reuses the dependency layers. The container runs as an unprivileged user. Compose starts the app after the database health check passes, and `--wait` returns once the app's own health check reports it as healthy. The app applies Flyway migrations, creates the initial administrator if needed and serves the frontend and API from the same process.

Sign in at [127.0.0.1 on port 8080](http://127.0.0.1:8080) and confirm the administrator can open the Overview. On a fresh database, the directory starts empty. Before an upgrade, back up the database and verify the new build. Run the same Compose command to rebuild and recreate the app container against the existing database.

`docker compose down` stops the service and retains the MySQL volume. Use a separate Compose project and database when rehearsing a restore or a release.

## Configuration

| Variable | Purpose |
| --- | --- |
| `DORM_DB_URL` | JDBC URL when launching the JAR against a database outside Compose. Keep `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` so timestamps are stored in UTC. The Compose app uses its internal MySQL service. |
| `DORM_DB_USERNAME` | MySQL account for this application. Defaults to `dormitory`. |
| `DORM_DB_PASSWORD` | Password for the application database account. |
| `DORM_ADMIN_USERNAME` | Username for the initial administrator. Defaults to `admin`. |
| `DORM_ADMIN_PASSWORD` | Initial password, at least 12 characters and at most 72 UTF-8 bytes. |
| `DORM_TIME_ZONE` | IANA time zone of the residence, such as `Asia/Shanghai` or `Europe/London`. Defaults to `UTC` in Compose and to the server's time zone when running the JAR directly. |
| `DORM_PORT` | Local host port published by Compose. Defaults to `8080`. |
| `DORM_SECURE_COOKIE` | Set to `true` when users access the app over HTTPS. |

Compose publishes the application on the loopback interface and keeps MySQL on its internal network. To provide remote access, terminate HTTPS at a reverse proxy and forward requests to the loopback port. Use a dedicated database account and configure TLS in the JDBC URL when MySQL is on a separate host.

The demo profile binds to loopback and uses a temporary database. Use the default profile with MySQL to store operational records.

### Dates and time zones

Timestamps, such as check-in and checkout times, are stored in UTC and shown in the residence's time zone. Calendar dates, such as the date of an absence, are the local day in the residence's time zone. "Today" therefore changes at local midnight, not at UTC midnight. An invalid `DORM_TIME_ZONE` stops the application at startup with an error naming the variable.

## Health checks and verification

`GET /actuator/health` reports `UP` when the application and its database are available and returns HTTP 503 otherwise. It is public and returns no component details. Liveness and readiness probes for orchestrators are at `/actuator/health/liveness` and `/actuator/health/readiness`. The image's Docker health check uses the main endpoint.

After a deployment or upgrade, run the smoke test with Node.js 20 or newer. It waits for the health check, confirms the deployment is not running the demo profile and signs in as the administrator.

```bash
DORM_ADMIN_PASSWORD='the administrator password' node scripts/smoke-test.mjs http://127.0.0.1:8080/
```

Add `--write` to also check a student in, record an absence for the current local day, export it as CSV and check the student out. The write check adds records, so use it only against a disposable database.

## Database lifecycle

Flyway runs versioned migrations before creating the first administrator. On an initialized database, use the authenticated password-change form for the administrator and the staff management page for staff accounts. Bootstrap credentials apply to the first startup.

Store database backups outside the Compose volume. This command writes a logical backup from a running Compose deployment. Restrict access to the output because it contains application records.

```bash
docker compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --single-transaction --no-tablespaces -u "$MYSQL_USER" "$MYSQL_DATABASE"' > dormitory-backup.sql
```

Restore into an empty database on a separate MySQL instance and verify that the app starts, Flyway validates the schema history and an existing administrator can sign in. The application account should retain the schema permissions it needs for migrations.

## Automated verification

GitHub Actions runs the [MySQL integration tests](testing.md#mysql-integration) against a fresh database. The Compose job builds the image from source, waits for both health checks and runs the smoke test with `--write` against a fresh volume.
