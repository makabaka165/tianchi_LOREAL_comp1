package com.hmdp.servicedata.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.ai.integration.support.IntegrationMySqlContainer;
import com.hmdp.servicedata.application.contract.ServiceDataImportCounts;
import com.hmdp.servicedata.application.contract.ServiceDataImportErrorPage;
import com.hmdp.servicedata.application.imports.ImportIssue;
import com.hmdp.servicedata.application.imports.ImportRows;
import com.hmdp.servicedata.application.imports.WorkbookParseResult;
import com.hmdp.servicedata.domain.model.ImportBatch;
import com.hmdp.servicedata.domain.model.ImportBatchStatus;
import com.hmdp.servicedata.domain.model.ImportErrorSeverity;
import com.hmdp.servicedata.domain.model.ScopeRef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class ServiceDataImportPersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-27T03:00:00Z");
    private static final ScopeRef SCOPE = new ScopeRef("tenant-a", "workspace-a");

    @Container
    static final IntegrationMySqlContainer MYSQL = new IntegrationMySqlContainer();

    private static JdbcTemplate jdbc;
    private static JdbcImportBatchRepository batches;
    private static JdbcImportStagingRepository staging;

    @BeforeAll
    static void migrate() {
        MYSQL.migrateSchema();
        jdbc = MYSQL.jdbcTemplate();
        batches = new JdbcImportBatchRepository(jdbc);
        staging = new JdbcImportStagingRepository(jdbc,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void persistsTypedPreviewAndPagedMaskedErrorsWithoutWritingFacts() {
        String batchId = "data003-persistence";
        ImportBatch batch = ImportBatch.startPreview(batchId, SCOPE, "data.xlsx",
                "a".repeat(64), "competition-workbook-v1", NOW.plusSeconds(3600));
        batches.insert(batch, "operator-create");
        WorkbookParseResult result = completeResult();
        ServiceDataImportCounts counts = ServiceDataImportCounts.from(result);

        staging.savePreview(SCOPE, batchId, batch.getStagingExpiresAt(), result, counts);
        batch.finishPreview(1, 1);
        assertThat(batches.updateWithVersion(batch, 0, "operator-preview")).isTrue();

        assertThat(staging.findCounts(SCOPE, batchId).total()).isEqualTo(counts.total());
        ServiceDataImportErrorPage first = staging.findErrors(SCOPE, batchId, 1, 1);
        ServiceDataImportErrorPage second = staging.findErrors(SCOPE, batchId, 2, 1);
        assertThat(first.getTotal()).isEqualTo(2);
        assertThat(first.getItems()).hasSize(1);
        assertThat(first.getItems().get(0).getSeverity()).isEqualTo("BLOCKING");
        assertThat(second.getItems().get(0).getMaskedValue()).isEqualTo("135***");
        assertThat(staging.findErrors(new ScopeRef("tenant-a", "workspace-b"),
                batchId, 1, 50).getTotal()).isZero();

        ImportBatch stored = batches.findById(SCOPE, batchId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ImportBatchStatus.REJECTED);
        assertThat(stored.getVersion()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select concat(created_by, ':', updated_by) from cs_data_import_batch where id = ?",
                String.class, batchId)).isEqualTo("operator-create:operator-preview");
        assertThat(formalFactCount()).isZero();

        String payloads = String.join("", jdbc.queryForList(
                "select payload_json from cs_data_import_staging where batch_id = ?",
                String.class, batchId));
        String report = String.join("", jdbc.queryForList(
                "select concat(coalesce(masked_raw_value,''), message) "
                        + "from cs_data_import_error where batch_id = ?",
                String.class, batchId));
        assertThat(payloads + report).doesNotContain(
                "scene_major", "scene_minor", "13512345678", "Bearer ", "token=");
    }

    @Test
    void previewBatchStagingAndErrorsRollBackTogether() {
        String batchId = "data003-rollback";
        TransactionTemplate transaction = new TransactionTemplate(
                new DataSourceTransactionManager(jdbc.getDataSource()));

        assertThatThrownBy(() -> transaction.executeWithoutResult(ignored -> {
            ImportBatch batch = ImportBatch.startPreview(batchId, SCOPE, "data.xlsx",
                    "b".repeat(64), "competition-workbook-v1", NOW.plusSeconds(3600));
            batches.insert(batch, "operator-rollback");
            WorkbookParseResult result = completeResult();
            staging.savePreview(SCOPE, batchId, batch.getStagingExpiresAt(), result,
                    ServiceDataImportCounts.from(result));
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject(
                "select count(*) from cs_data_import_batch where id = ?", Integer.class, batchId))
                .isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from cs_data_import_staging where batch_id = ?", Integer.class,
                batchId)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from cs_data_import_error where batch_id = ?", Integer.class,
                batchId)).isZero();
    }

    private static WorkbookParseResult completeResult() {
        WorkbookParseResult result = new WorkbookParseResult("competition-workbook-v1");
        result.getAliases().add(new ImportRows.ConsumerAliasRow("方***", "chat"));
        result.getConversations().add(new ImportRows.ConversationRow(
                "S00082", "方***", "chat", 1, NOW, NOW));
        result.getMessages().add(new ImportRows.MessageRow(
                "S00082", 1, "msg-1", NOW, "CONSUMER", "方***", "脱敏消息",
                "TEXT", null, null, 2));
        result.getOrders().add(new ImportRows.OrderRow(
                "000123", "S00082", "方***", "orders", "sku-1", "商品",
                1, BigDecimal.ONE, BigDecimal.ONE, "PAID", NOW, NOW, null,
                null, null, Collections.emptyMap(), 2));
        result.getServiceCases().add(new ImportRows.ServiceCaseRow(
                "case-1", "不良反应工单", "S00082", "000123", "方***", "cases",
                "OPEN", "reason", "消费者自述", NOW, null, Collections.emptyMap(), 2));
        result.getLinks().add(new ImportRows.SourceLinkRow(
                "CONVERSATION_ORDER", "S00082", "000123", false));
        result.getIssues().add(new ImportIssue(
                "订单", 3, "日期", "INVALID_DATETIME", ImportErrorSeverity.BLOCKING,
                "bad***", "日期格式无效"));
        result.getIssues().add(new ImportIssue(
                "订单", 4, "支付宝账号", "INVALID_ACCOUNT", ImportErrorSeverity.WARNING,
                "135***", "账号格式无效"));
        return result;
    }

    private static int formalFactCount() {
        return List.of("cs_data_consumer", "cs_data_consumer_alias", "cs_data_conversation",
                        "cs_data_message", "cs_data_order_snapshot", "cs_data_service_case",
                        "cs_data_source_link")
                .stream()
                .mapToInt(table -> jdbc.queryForObject(
                        "select count(*) from " + table, Integer.class))
                .sum();
    }
}
