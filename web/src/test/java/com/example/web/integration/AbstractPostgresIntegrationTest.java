package com.example.web.integration;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Base class for the "thin integration layer" of *IT tests that run against
 * a real, disposable PostgreSQL container (Testcontainers) instead of a
 * mocked repository - covering the handful of DB behaviors a mock can never
 * validate: FK/CHECK constraint enforcement, ON DELETE/ON UPDATE cascade
 * actions, and JPQL null-handling semantics.
 *
 * Bound to Failsafe (mvn verify), not Surefire (mvn test) - see web/pom.xml -
 * so these never run as part of the fast, Docker-free default build.
 *
 * Uses the "singleton container" pattern: the container is started once in a
 * static initializer (not JUnit's per-class @Container lifecycle) and left
 * running for the whole JVM, reaped by Testcontainers' Ryuk container on
 * exit. Every subclass shares the one container and the one schema load.
 *
 * @DataJpaTest + @AutoConfigureTestDatabase(replace = NONE) keeps the layer
 * genuinely thin: no full application context, no security filter chain -
 * just the JPA repositories under test, each test method wrapped in a
 * transaction that rolls back automatically so tests never leak data into
 * each other.
 *
 * @DataJpaTest defaults spring.jpa.hibernate.ddl-auto to create-drop, which
 * would let Hibernate generate its own schema from the JPA entity mappings
 * over the one just loaded from database/schema_block_scheduling.sql above -
 * silently dropping every FK/CHECK constraint this test layer exists to
 * verify, since none of those are represented in the entity classes (they're
 * plain String fields). Forced back to "none" so the hand-loaded schema.sql
 * stays authoritative.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")
public abstract class AbstractPostgresIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            ScriptUtils.executeSqlScript(connection, new org.springframework.core.io.FileSystemResource(findSchemaFile()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load database/schema_block_scheduling.sql into the Testcontainers Postgres instance", e);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /**
     * Walks upward from the JVM's working directory looking for
     * database/schema_block_scheduling.sql - `mvn -pl web verify` from the
     * repo root still runs the web module's tests with the web module's own
     * directory as cwd, same reasoning as RepoRootResolver in main source.
     */
    private static File findSchemaFile() {
        File dir = new File(System.getProperty("user.dir", "."));
        for (int i = 0; i < 4 && dir != null; i++) {
            File candidate = new File(dir, "database/schema_block_scheduling.sql");
            if (candidate.isFile()) {
                return candidate;
            }
            dir = dir.getParentFile();
        }
        throw new IllegalStateException("Could not locate database/schema_block_scheduling.sql above " + System.getProperty("user.dir"));
    }
}
