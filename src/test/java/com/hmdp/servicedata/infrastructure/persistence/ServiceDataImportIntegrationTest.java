package com.hmdp.servicedata.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.ai.integration.support.IntegrationMySqlContainer;
import com.hmdp.common.ErrorCode;
import com.hmdp.exception.BusinessException;
import com.hmdp.servicedata.application.contract.ConfirmServiceDataImportCommand;
import com.hmdp.servicedata.application.contract.ServiceDataImportConfirmationView;
import com.hmdp.servicedata.application.contract.ServiceDataImportCounts;
import com.hmdp.servicedata.application.contract.ServiceDataImportScope;
import com.hmdp.servicedata.application.event.ServiceFactsImported;
import com.hmdp.servicedata.application.imports.ImportRows;
import com.hmdp.servicedata.application.imports.WorkbookParseResult;
import com.hmdp.servicedata.application.port.out.ServiceFactsImportedEventPublisher;
import com.hmdp.servicedata.application.port.out.WorkbookParserPort;
import com.hmdp.servicedata.application.service.ConsumerIdentityResolutionPolicy;
import com.hmdp.servicedata.application.service.ServiceDataImportApplicationService;
import com.hmdp.servicedata.application.service.ServiceDataImportCommitService;
import com.hmdp.servicedata.domain.model.ImportBatch;
import com.hmdp.servicedata.domain.model.ImportBatchStatus;
import com.hmdp.servicedata.domain.model.ScopeRef;
import com.hmdp.servicedata.infrastructure.event.SpringServiceFactsImportedEventPublisher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class ServiceDataImportIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-27T05:00:00Z");
    private static final String PARSER_VERSION = "competition-workbook-v1";

    @Container
    static final IntegrationMySqlContainer MYSQL = new IntegrationMySqlContainer();

    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static ObjectMapper mapper;
    private static JdbcImportBatchRepository batches;
    private static JdbcImportStagingRepository staging;
    private static ServiceDataImportCommitService committer;

    @BeforeAll
    static void migrate() {
        MYSQL.migrateSchema();
        jdbc = MYSQL.jdbcTemplate();
        transactions = new TransactionTemplate(
                new DataSourceTransactionManager(jdbc.getDataSource()));
        mapper = new ObjectMapper().findAndRegisterModules();
        batches = new JdbcImportBatchRepository(jdbc);
        staging = new JdbcImportStagingRepository(jdbc, mapper);
        committer = new ServiceDataImportCommitService(staging,
                new JdbcConsumerRepository(jdbc), new JdbcConsumerAliasRepository(jdbc),
                new JdbcConversationRepository(jdbc), new JdbcMessageRepository(jdbc),
                new JdbcOrderSnapshotRepository(jdbc), new JdbcServiceCaseRepository(jdbc),
                new JdbcSourceLinkRepository(jdbc), new ConsumerIdentityResolutionPolicy(), mapper);
    }

    @Test
    void confirmAtomicallyCommitsFactsMergesAliasesCleansStagingAndPublishesPiiFreeEvent()
            throws Exception {
        ScopeRef scope = new ScopeRef("tenant-atomic", "workspace-atomic");
        String batchId = "data004-atomic";
        WorkbookParseResult result = completeResult("atomic", BigDecimal.TEN, "OPEN");
        result.getConversations().add(new ImportRows.ConversationRow(
                "S-atomic-2", "方***", "chat:S-atomic-2", 1, NOW, NOW));
        result.getMessages().add(new ImportRows.MessageRow(
                "S-atomic-2", 1, "msg-atomic-2", NOW, "CONSUMER", "方***",
                "另一条消费者敏感正文", "TEXT", null, null, 3));
        stageReady(batchId, scope, result);
        List<ServiceFactsImported> published = new CopyOnWriteArrayList<>();

        ServiceDataImportConfirmationView confirmed = confirm(batchId, scope,
                afterCommit(event -> published.add((ServiceFactsImported) event)));

        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
        assertThat(confirmed.getVersion()).isEqualTo(3);
        assertThat(confirmed.getCreated().getConsumerAliases()).isEqualTo(1);
        assertThat(confirmed.getCreated().getConversations()).isEqualTo(2);
        assertThat(confirmed.getCreated().getMessages()).isEqualTo(2);
        assertThat(confirmed.getCreated().getOrderSnapshots()).isEqualTo(1);
        assertThat(confirmed.getCreated().getServiceCases()).isEqualTo(1);
        assertThat(confirmed.getCreated().getSourceLinks()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(distinct consumer_id) from cs_data_conversation "
                        + "where tenant_id = ? and workspace_id = ?",
                Integer.class, scope.getTenantId(), scope.getWorkspaceId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from cs_data_import_staging where batch_id = ?",
                Integer.class, batchId)).isZero();
        assertThat(jdbc.queryForObject(
                "select concat(status, ':', version, ':', confirmed_by) "
                        + "from cs_data_import_batch where id = ?",
                String.class, batchId)).isEqualTo("CONFIRMED:3:operator-1");
        assertThat(jdbc.queryForObject(
                "select commit_counts_json from cs_data_import_batch where id = ?",
                String.class, batchId)).contains("created", "skipped");
        assertThat(published).hasSize(1);
        String eventJson = mapper.writeValueAsString(published.get(0));
        assertThat(eventJson).doesNotContain(
                "方***", "消费者敏感正文", "scene_major", "scene_minor", "token");
    }

    @Test
    void aSecondBatchWithIdenticalFactsReportsOnlySkippedAndAddsNoRows() {
        ScopeRef scope = new ScopeRef("tenant-repeat", "workspace-repeat");
        WorkbookParseResult result = completeResult("repeat", BigDecimal.TEN, "OPEN");
        stageReady("data004-repeat-1", scope, result);
        confirm("data004-repeat-1", scope, afterCommit(event -> { }));
        int before = formalFactCount(scope);
        stageReady("data004-repeat-2", scope, result);

        ServiceDataImportConfirmationView repeated = confirm("data004-repeat-2", scope,
                afterCommit(event -> { }));

        assertThat(repeated.getCreated().total()).isZero();
        assertThat(repeated.getUpdated().total()).isZero();
        assertThat(repeated.getSkipped().getConsumerAliases()).isEqualTo(1);
        assertThat(repeated.getSkipped().getConversations()).isEqualTo(1);
        assertThat(repeated.getSkipped().getMessages()).isEqualTo(1);
        assertThat(repeated.getSkipped().getOrderSnapshots()).isEqualTo(1);
        assertThat(repeated.getSkipped().getServiceCases()).isEqualTo(1);
        assertThat(repeated.getSkipped().getSourceLinks()).isEqualTo(2);
        assertThat(formalFactCount(scope)).isEqualTo(before);
    }

    @Test
    void changedOrderAndCaseAppendNewVersionsWithoutOverwritingHistory() {
        ScopeRef scope = new ScopeRef("tenant-version", "workspace-version");
        WorkbookParseResult original = completeResult("version", BigDecimal.TEN, "OPEN");
        stageReady("data004-version-1", scope, original);
        confirm("data004-version-1", scope, afterCommit(event -> { }));
        WorkbookParseResult changed = completeResult(
                "version", BigDecimal.valueOf(20), "CLOSED");
        stageReady("data004-version-2", scope, changed);

        ServiceDataImportConfirmationView updated = confirm("data004-version-2", scope,
                afterCommit(event -> { }));

        assertThat(updated.getUpdated().getOrderSnapshots()).isEqualTo(1);
        assertThat(updated.getUpdated().getServiceCases()).isEqualTo(1);
        assertThat(updated.getCreated().total()).isZero();
        assertThat(jdbc.queryForList(
                "select snapshot_seq from cs_data_order_snapshot where tenant_id = ? "
                        + "and workspace_id = ? order by snapshot_seq",
                Integer.class, scope.getTenantId(), scope.getWorkspaceId()))
                .containsExactly(1, 2);
        assertThat(jdbc.queryForList(
                "select amount from cs_data_order_snapshot where tenant_id = ? "
                        + "and workspace_id = ? order by snapshot_seq",
                BigDecimal.class, scope.getTenantId(), scope.getWorkspaceId()))
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("20.00"));
        assertThat(jdbc.queryForList(
                "select case_status from cs_data_service_case where tenant_id = ? "
                        + "and workspace_id = ? order by case_seq",
                String.class, scope.getTenantId(), scope.getWorkspaceId()))
                .containsExactly("OPEN", "CLOSED");
    }

    @Test
    void danglingLinkRollsBackPartialFactsAndRestoresReadyBatchWithStaging() {
        ScopeRef scope = new ScopeRef("tenant-rollback", "workspace-rollback");
        String batchId = "data004-rollback";
        WorkbookParseResult result = completeResult("rollback", BigDecimal.TEN, "OPEN");
        result.getOrders().clear();
        stageReady(batchId, scope, result);
        List<ServiceFactsImported> published = new CopyOnWriteArrayList<>();

        assertThatThrownBy(() -> confirm(batchId, scope,
                afterCommit(event -> published.add((ServiceFactsImported) event))))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.CS_IMPORT_CONFLICT));

        ImportBatch stored = batches.findById(scope, batchId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ImportBatchStatus.READY_TO_CONFIRM);
        assertThat(stored.getVersion()).isEqualTo(1);
        assertThat(formalFactCount(scope)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from cs_data_import_staging where batch_id = ?",
                Integer.class, batchId)).isGreaterThan(0);
        assertThat(published).isEmpty();
    }

    @Test
    void concurrentConfirmAllowsExactlyOneCommitter() throws Exception {
        ScopeRef scope = new ScopeRef("tenant-concurrent", "workspace-concurrent");
        String batchId = "data004-concurrent";
        stageReady(batchId, scope, completeResult("concurrent", BigDecimal.TEN, "OPEN"));
        List<ServiceFactsImported> published = new CopyOnWriteArrayList<>();
        ServiceDataImportApplicationService application = application(
                afterCommit(event -> published.add((ServiceFactsImported) event)));
        ConfirmServiceDataImportCommand command = command(batchId, scope);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                attempts.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        TransactionTemplate transaction = new TransactionTemplate(
                                new DataSourceTransactionManager(jdbc.getDataSource()));
                        return transaction.execute(status -> application.confirm(
                                requestScope(scope), batchId, command)).getStatus();
                    } catch (BusinessException conflict) {
                        return conflict.getErrorCode().name();
                    }
                }));
            }
            ready.await();
            start.countDown();
            List<String> results = List.of(attempts.get(0).get(), attempts.get(1).get());
            assertThat(results).containsExactlyInAnyOrder("CONFIRMED", "CS_IMPORT_CONFLICT");
        } finally {
            executor.shutdownNow();
        }
        assertThat(batches.findById(scope, batchId).orElseThrow().getStatus())
                .isEqualTo(ImportBatchStatus.CONFIRMED);
        assertThat(published).hasSize(1);
    }

    @Test
    void failingAfterCommitListenerCannotRollBackConfirmedFacts() {
        ScopeRef scope = new ScopeRef("tenant-listener", "workspace-listener");
        String batchId = "data004-listener";
        stageReady(batchId, scope, completeResult("listener", BigDecimal.TEN, "OPEN"));

        ServiceDataImportConfirmationView confirmed = confirm(batchId, scope,
                afterCommit(event -> {
                    throw new IllegalStateException("token=listener-secret consumer=方***");
                }));

        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
        assertThat(batches.findById(scope, batchId).orElseThrow().getStatus())
                .isEqualTo(ImportBatchStatus.CONFIRMED);
        assertThat(formalFactCount(scope)).isGreaterThan(0);
    }

    private static void stageReady(String batchId, ScopeRef scope, WorkbookParseResult result) {
        transactions.executeWithoutResult(ignored -> {
            ImportBatch batch = ImportBatch.startPreview(batchId, scope, "uploaded-workbook.xlsx",
                    sha256(batchId), PARSER_VERSION, NOW.plusSeconds(3600));
            batches.insert(batch, "operator-1");
            ServiceDataImportCounts counts = ServiceDataImportCounts.from(result);
            staging.savePreview(scope, batchId, batch.getStagingExpiresAt(), result, counts);
            batch.finishPreview(0, 0);
            if (!batches.updateWithVersion(batch, 0, "operator-1")) {
                throw new IllegalStateException("failed to prepare integration batch");
            }
        });
    }

    private static ServiceDataImportConfirmationView confirm(
            String batchId, ScopeRef scope, ServiceFactsImportedEventPublisher publisher) {
        ServiceDataImportApplicationService application = application(publisher);
        ConfirmServiceDataImportCommand command = command(batchId, scope);
        return transactions.execute(status ->
                application.confirm(requestScope(scope), batchId, command));
    }

    private static ConfirmServiceDataImportCommand command(String batchId, ScopeRef scope) {
        ImportBatch batch = batches.findById(scope, batchId).orElseThrow();
        return new ConfirmServiceDataImportCommand(batch.getSourceSha256(), PARSER_VERSION,
                batch.getVersion(), true);
    }

    private static ServiceDataImportApplicationService application(
            ServiceFactsImportedEventPublisher publisher) {
        WorkbookParserPort parser = new WorkbookParserPort() {
            @Override
            public String parserVersion() {
                return PARSER_VERSION;
            }

            @Override
            public WorkbookParseResult parse(InputStream input, long declaredSize) {
                throw new UnsupportedOperationException("confirm does not parse the workbook");
            }
        };
        return new ServiceDataImportApplicationService(batches, staging, parser, committer,
                publisher, true, true, 1024 * 1024, 24,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ServiceFactsImportedEventPublisher afterCommit(
            org.springframework.context.ApplicationEventPublisher publisher) {
        return new SpringServiceFactsImportedEventPublisher(publisher);
    }

    private static ServiceDataImportScope requestScope(ScopeRef scope) {
        return new ServiceDataImportScope(scope.getTenantId(), scope.getWorkspaceId(),
                "operator-1");
    }

    private static WorkbookParseResult completeResult(String suffix, BigDecimal paidAmount,
                                                       String caseStatus) {
        String conversationId = "S-" + suffix;
        String orderNo = "000-" + suffix;
        String caseNo = "case-" + suffix;
        WorkbookParseResult result = new WorkbookParseResult(PARSER_VERSION);
        result.getAliases().add(new ImportRows.ConsumerAliasRow("方***", "chat"));
        result.getConversations().add(new ImportRows.ConversationRow(
                conversationId, "方***", "chat:" + conversationId, 1, NOW, NOW));
        result.getMessages().add(new ImportRows.MessageRow(
                conversationId, 1, "msg-" + suffix, NOW, "CONSUMER", "方***",
                "消费者敏感正文", "TEXT", null, null, 2));
        result.getOrders().add(new ImportRows.OrderRow(
                orderNo, conversationId, "方***", "orders", "sku-1", "商品", 1,
                BigDecimal.ONE, paidAmount, "PAID", NOW, NOW, null, null, null,
                Collections.emptyMap(), 2));
        result.getServiceCases().add(new ImportRows.ServiceCaseRow(
                caseNo, "不良反应工单", conversationId, orderNo, "方***", "cases:不良反应工单",
                caseStatus, "reason", "消费者自述", NOW, null,
                Collections.emptyMap(), 2));
        result.getLinks().add(new ImportRows.SourceLinkRow(
                "CONVERSATION_ORDER", conversationId, orderNo, false));
        result.getLinks().add(new ImportRows.SourceLinkRow(
                "CONVERSATION_CASE", conversationId, caseNo, false));
        return result;
    }

    private static int formalFactCount(ScopeRef scope) {
        return List.of("cs_data_consumer", "cs_data_consumer_alias", "cs_data_conversation",
                        "cs_data_message", "cs_data_order_snapshot", "cs_data_service_case",
                        "cs_data_source_link")
                .stream()
                .mapToInt(table -> jdbc.queryForObject(
                        "select count(*) from " + table + " where tenant_id = ? and workspace_id = ?",
                        Integer.class, scope.getTenantId(), scope.getWorkspaceId()))
                .sum();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte current : digest) {
                hex.append(String.format("%02x", current & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
