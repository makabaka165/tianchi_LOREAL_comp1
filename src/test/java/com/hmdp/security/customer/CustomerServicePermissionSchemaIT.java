package com.hmdp.security.customer;

import com.hmdp.ai.integration.support.IntegrationMySqlContainer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates V20260726_01__customer_service_permissions.sql on a clean database through
 * the full staged migration chain, and proves the seed is idempotent by replaying its
 * statements against an already-migrated schema.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class CustomerServicePermissionSchemaIT {

    @Container
    static final IntegrationMySqlContainer MYSQL = new IntegrationMySqlContainer();

    private static final String[] EXPECTED_CODES = {
            "cs:data:import",
            "cs:workspace:read",
            "cs:assist:request",
            "cs:suggestion:decide",
            "cs:risk:read",
            "cs:risk:manage"
    };

    @Test
    void migrationSeedsSixPermissionsBoundToAdminAndStaysIdempotent() throws Exception {
        MYSQL.migrateSchema();
        JdbcTemplate jdbc = MYSQL.jdbcTemplate();

        assertSeedState(jdbc);

        // Replaying the seed must not duplicate rows (ON DUPLICATE KEY UPDATE + INSERT IGNORE).
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V20260726_01__customer_service_permissions.sql"),
                StandardCharsets.UTF_8);
        String withoutComments = Arrays.stream(sql.split("\r?\n"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"));
        for (String statement : withoutComments.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                jdbc.execute(trimmed);
            }
        }

        assertSeedState(jdbc);
    }

    private void assertSeedState(JdbcTemplate jdbc) {
        for (String code : EXPECTED_CODES) {
            Integer rows = jdbc.queryForObject(
                    "select count(*) from sys_permission where permission_code = ? and status = 1",
                    Integer.class, code);
            assertThat(rows).as("sys_permission row for %s", code).isEqualTo(1);
        }
        Integer csPermissions = jdbc.queryForObject(
                "select count(*) from sys_permission where permission_code like 'cs:%'",
                Integer.class);
        assertThat(csPermissions).isEqualTo(6);
        Integer adminBindings = jdbc.queryForObject(
                "select count(*) from sys_role_permission rp "
                        + "join sys_role r on r.id = rp.role_id "
                        + "join sys_permission p on p.id = rp.permission_id "
                        + "where r.role_key = 'admin' and p.permission_code like 'cs:%' and rp.status = 1",
                Integer.class);
        assertThat(adminBindings).isEqualTo(6);
    }
}
