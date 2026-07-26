package com.hmdp.servicedata.infrastructure.persistence;

import com.hmdp.ai.integration.support.IntegrationMySqlContainer;
import com.hmdp.servicedata.domain.model.ImportBatch;
import com.hmdp.servicedata.domain.model.ImportBatchStatus;
import com.hmdp.servicedata.domain.model.ScopeRef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates V20260726_02: all cs_data_* tables exist with scope columns, no evaluation
 * label columns leak into the schema, business numbers keep leading zeros, one
 * conversation may link to many orders, and source-identity unique keys fire.
 */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class ServiceDataSchemaIntegrationTest {

    @Container
    static final IntegrationMySqlContainer MYSQL = new IntegrationMySqlContainer();

    private static JdbcTemplate jdbc;

    private static final List<String> TABLES = List.of(
            "cs_data_import_batch", "cs_data_import_staging", "cs_data_import_error",
            "cs_data_consumer", "cs_data_consumer_alias", "cs_data_conversation",
            "cs_data_message", "cs_data_order_snapshot", "cs_data_service_case",
            "cs_data_source_link");

    @BeforeAll
    static void migrate() {
        MYSQL.migrateSchema();
        jdbc = MYSQL.jdbcTemplate();
    }

    @Test
    void allServiceDataTablesExistWithScopeAndAudit() {
        for (String table : TABLES) {
            Integer exists = jdbc.queryForObject(
                    "select count(*) from information_schema.tables "
                            + "where table_schema = database() and table_name = ?",
                    Integer.class, table);
            assertThat(exists).as("table %s", table).isEqualTo(1);
            for (String column : List.of("tenant_id", "workspace_id", "created_at")) {
                Integer col = jdbc.queryForObject(
                        "select count(*) from information_schema.columns "
                                + "where table_schema = database() and table_name = ? and column_name = ?",
                        Integer.class, table, column);
                assertThat(col).as("%s.%s", table, column).isEqualTo(1);
            }
        }
    }

    @Test
    void noEvaluationLabelColumnsAnywhereInServiceData() {
        Integer leaked = jdbc.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = database() and table_name like 'cs\\_data\\_%' "
                        + "and (column_name like 'scene%' or column_name like 'target%')",
                Integer.class);
        assertThat(leaked).isZero();
    }

    @Test
    void businessNumbersAreStringsAndKeepLeadingZeros() {
        String orderNoType = jdbc.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_schema = database() and table_name = 'cs_data_order_snapshot' "
                        + "and column_name = 'order_no'",
                String.class);
        assertThat(orderNoType).isEqualTo("varchar");

        jdbc.update("insert into cs_data_order_snapshot (id, tenant_id, workspace_id, order_no, "
                        + "snapshot_seq, source_system, content_hash) values (?,?,?,?,?,?,?)",
                "it-snap-1", "default", "default", "0007202606150001", 1, "workbook",
                "b5ac027e863c5580dab39c8f459e4698d65e9fbec29832c9915448f2087307b7");
        String roundTrip = jdbc.queryForObject(
                "select order_no from cs_data_order_snapshot where id = 'it-snap-1'", String.class);
        assertThat(roundTrip).isEqualTo("0007202606150001");
    }

    @Test
    void oneConversationLinksToManyOrders() {
        jdbc.update("insert into cs_data_source_link (id, tenant_id, workspace_id, link_type, "
                        + "from_id, to_ref) values (?,?,?,?,?,?)",
                "it-link-1", "default", "default", "CONVERSATION_ORDER", "conv-x", "order-A");
        jdbc.update("insert into cs_data_source_link (id, tenant_id, workspace_id, link_type, "
                        + "from_id, to_ref) values (?,?,?,?,?,?)",
                "it-link-2", "default", "default", "CONVERSATION_ORDER", "conv-x", "order-B");
        Integer links = jdbc.queryForObject(
                "select count(*) from cs_data_source_link where from_id = 'conv-x'", Integer.class);
        assertThat(links).isEqualTo(2);

        assertThatThrownBy(() -> jdbc.update(
                "insert into cs_data_source_link (id, tenant_id, workspace_id, link_type, "
                        + "from_id, to_ref) values (?,?,?,?,?,?)",
                "it-link-3", "default", "default", "CONVERSATION_ORDER", "conv-x", "order-A"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void aliasIdentityIsUniquePerScopeAndSource() {
        jdbc.update("insert into cs_data_consumer (id, tenant_id, workspace_id, display_name, "
                        + "created_by, updated_by) values (?,?,?,?,?,?)",
                "it-consumer-1", "default", "default", "小美", "system", "system");
        jdbc.update("insert into cs_data_consumer_alias (id, tenant_id, workspace_id, consumer_id, "
                        + "source_system, source_scope, display_alias, normalized_alias_hash) "
                        + "values (?,?,?,?,?,?,?,?)",
                "it-alias-1", "default", "default", "it-consumer-1", "workbook", "conversations",
                "小美", "a".repeat(64));
        assertThatThrownBy(() -> jdbc.update(
                "insert into cs_data_consumer_alias (id, tenant_id, workspace_id, consumer_id, "
                        + "source_system, source_scope, display_alias, normalized_alias_hash) "
                        + "values (?,?,?,?,?,?,?,?)",
                "it-alias-2", "default", "default", "it-consumer-1", "workbook", "conversations",
                "小美", "a".repeat(64)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void duplicateMessageSourceKeyIsRejected() {
        jdbc.update("insert into cs_data_message (id, tenant_id, workspace_id, conversation_id, "
                        + "source_message_key, sender_role, content, source_sequence) "
                        + "values (?,?,?,?,?,?,?,?)",
                "it-msg-1", "default", "default", "conv-y", "S00082-1", "CONSUMER", "你好", 1);
        assertThatThrownBy(() -> jdbc.update(
                "insert into cs_data_message (id, tenant_id, workspace_id, conversation_id, "
                        + "source_message_key, sender_role, content, source_sequence) "
                        + "values (?,?,?,?,?,?,?,?)",
                "it-msg-2", "default", "default", "conv-y", "S00082-1", "CONSUMER", "重复", 2))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void importBatchRepositoryRoundTripWithOptimisticLocking() {
        JdbcImportBatchRepository repository = new JdbcImportBatchRepository(jdbc);
        ScopeRef scope = new ScopeRef("default", "default");
        String sha = "c".repeat(64);
        ImportBatch batch = ImportBatch.startPreview("it-batch-1", scope, "data.xlsx", sha,
                "workbook-v1", Instant.now().plusSeconds(3600));
        repository.insert(batch);

        Optional<ImportBatch> loaded = repository.findById(scope, "it-batch-1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getStatus()).isEqualTo(ImportBatchStatus.PREVIEWING);

        ImportBatch working = loaded.get();
        working.finishPreview(1, 0);
        assertThat(repository.updateWithVersion(working, 0)).isTrue();
        assertThat(repository.updateWithVersion(working, 0))
                .as("second writer with stale version must lose")
                .isFalse();

        Optional<ImportBatch> reusable = repository.findReusable(scope, sha, "workbook-v1");
        assertThat(reusable).isPresent();
        assertThat(reusable.get().getId()).isEqualTo("it-batch-1");

        assertThat(repository.findById(new ScopeRef("default", "other-workspace"), "it-batch-1"))
                .as("cross-workspace read must miss")
                .isEmpty();
    }
}
