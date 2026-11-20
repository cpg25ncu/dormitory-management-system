package io.github.cpg25ncu.dormitory;

import java.util.Locale;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Chooses the database for the integration tests. The suite uses the demo profile's in-memory H2 database by
 * default. With {@code -Ddormitory.test.database=mysql} it starts MySQL 8.4 in a container once per test run,
 * which needs a running Docker engine.
 */
final class TestDatabase {
    private static MySQLContainer mysql;

    private TestDatabase() {}

    static synchronized void register(DynamicPropertyRegistry registry) {
        String database = System.getProperty("dormitory.test.database", "h2").strip().toLowerCase(Locale.ROOT);
        switch (database) {
            case "h2" -> { }
            case "mysql" -> {
                if (mysql == null) {
                    // The same session time zone settings as a deployment, so timestamps are stored in UTC.
                    mysql = new MySQLContainer("mysql:8.4").withDatabaseName("dormitory_test")
                            .withUrlParam("connectionTimeZone", "UTC")
                            .withUrlParam("forceConnectionTimeZoneToSession", "true");
                    mysql.start();
                }
                registry.add("spring.datasource.url", mysql::getJdbcUrl);
                registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
                registry.add("spring.datasource.username", mysql::getUsername);
                registry.add("spring.datasource.password", mysql::getPassword);
            }
            default -> throw new IllegalStateException("dormitory.test.database must be h2 or mysql, not " + database + ".");
        }
    }
}
