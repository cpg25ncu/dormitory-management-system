# Deployment and operations

## Configuration

| Variable | Purpose |
| --- | --- |
| `DORM_DB_URL` | JDBC URL. Set this when running against a database outside Compose. |
| `DORM_DB_USERNAME` | MySQL account for this application. Defaults to `dormitory`. |
| `DORM_DB_PASSWORD` | Password for the application database account. |
| `DORM_ADMIN_USERNAME` | Username for the initial administrator. Defaults to `admin`. |
| `DORM_ADMIN_PASSWORD` | Initial password, at least 12 characters and at most 72 UTF-8 bytes. |
| `DORM_PORT` | Local host port published by Compose. Defaults to `8080`. |
| `DORM_SECURE_COOKIE` | Set to `true` when users access the app over HTTPS. |

Compose publishes the application on the loopback interface and keeps MySQL on its internal network. To provide remote access, terminate HTTPS at a reverse proxy and forward requests to the loopback port. Use a dedicated database account and configure TLS in the JDBC URL when MySQL is on a separate host.

The demo profile binds to loopback and uses a temporary database. Use the default profile with MySQL to store operational records. Dates in absence records and timestamps on report pages are presented in UTC.

## Database lifecycle

Flyway runs versioned migrations before creating the first administrator. On an initialized database, changing the bootstrap environment variables does not change the stored account. Use the authenticated password-change form for the administrator and the staff management page for staff accounts.

Store database backups outside the Compose volume. This command writes a logical backup from a running Compose deployment. Restrict access to the output because it contains application records.

```bash
docker compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --no-tablespaces -u "$MYSQL_USER" "$MYSQL_DATABASE"' > dormitory-backup.sql
```

Restore and verify backups against a separate database before applying changes to a live deployment. The application account should retain the schema permissions it needs for migrations.

## Integration testing against MySQL

Run `mvn verify` with `DORM_TEST_DB_URL`, `DORM_TEST_DB_USERNAME` and `DORM_TEST_DB_PASSWORD` pointed at an empty test database. The suite initializes and writes fixture data. The MySQL CI job uses a separate database for every run.
