# Deployment and operations

## Start or update a deployment

Use Java 17 or newer and Docker Compose. From the repository root, copy `.env.example` to `.env` and set distinct passwords for MySQL and the initial administrator.

```bash
sh ./mvnw -B clean verify
docker compose config --quiet
docker compose up --build -d
docker compose logs -f app
```

In PowerShell, use `.\mvnw.cmd -B clean verify` for the build. Compose starts the app after the database health check passes. The app applies Flyway migrations, creates the initial administrator if needed and serves the frontend and API from the same JAR.

Sign in at [127.0.0.1 on port 8080](http://127.0.0.1:8080) and confirm the administrator can open the Overview. On a fresh database, the directory starts empty. Before an upgrade, back up the database and verify the new build. Run the same Compose command to rebuild and recreate the app container against the existing database.

`docker compose down` stops the service and retains the MySQL volume. Use a separate Compose project and database when rehearsing a restore or a release.

## Configuration

| Variable | Purpose |
| --- | --- |
| `DORM_DB_URL` | JDBC URL when launching the JAR against a database outside Compose. The Compose app uses its internal MySQL service. |
| `DORM_DB_USERNAME` | MySQL account for this application. Defaults to `dormitory`. |
| `DORM_DB_PASSWORD` | Password for the application database account. |
| `DORM_ADMIN_USERNAME` | Username for the initial administrator. Defaults to `admin`. |
| `DORM_ADMIN_PASSWORD` | Initial password, at least 12 characters and at most 72 UTF-8 bytes. |
| `DORM_PORT` | Local host port published by Compose. Defaults to `8080`. |
| `DORM_SECURE_COOKIE` | Set to `true` when users access the app over HTTPS. |

Compose publishes the application on the loopback interface and keeps MySQL on its internal network. To provide remote access, terminate HTTPS at a reverse proxy and forward requests to the loopback port. Use a dedicated database account and configure TLS in the JDBC URL when MySQL is on a separate host.

The demo profile binds to loopback and uses a temporary database. Use the default profile with MySQL to store operational records. Dates in absence records and timestamps on report pages are presented in UTC.

## Database lifecycle

Flyway runs versioned migrations before creating the first administrator. On an initialized database, use the authenticated password-change form for the administrator and the staff management page for staff accounts. Bootstrap credentials apply to the first startup.

Store database backups outside the Compose volume. This command writes a logical backup from a running Compose deployment. Restrict access to the output because it contains application records.

```bash
docker compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --single-transaction --no-tablespaces -u "$MYSQL_USER" "$MYSQL_DATABASE"' > dormitory-backup.sql
```

Restore into an empty database on a separate MySQL instance and verify that the app starts, Flyway validates the schema history and an existing administrator can sign in. The application account should retain the schema permissions it needs for migrations.

## Automated verification

GitHub Actions runs the [MySQL integration tests](testing.md#mysql-integration) against a fresh database. The Compose job uses the JAR from the clean build and verifies startup and administrator sign-in with a fresh volume.
