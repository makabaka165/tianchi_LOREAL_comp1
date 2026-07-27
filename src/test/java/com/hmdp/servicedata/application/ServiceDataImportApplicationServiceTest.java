package com.hmdp.servicedata.application;

import com.hmdp.common.ErrorCode;
import com.hmdp.exception.BusinessException;
import com.hmdp.servicedata.application.contract.ConfirmServiceDataImportCommand;
import com.hmdp.servicedata.application.contract.ServiceDataImportBatchView;
import com.hmdp.servicedata.application.contract.ServiceDataImportConfirmationView;
import com.hmdp.servicedata.application.contract.ServiceDataImportCounts;
import com.hmdp.servicedata.application.contract.ServiceDataImportErrorPage;
import com.hmdp.servicedata.application.contract.ServiceDataImportScope;
import com.hmdp.servicedata.application.contract.ServiceDataImportUpload;
import com.hmdp.servicedata.application.imports.ImportIssue;
import com.hmdp.servicedata.application.imports.ImportRows;
import com.hmdp.servicedata.application.imports.WorkbookParseResult;
import com.hmdp.servicedata.application.port.out.ImportStagingPort;
import com.hmdp.servicedata.application.port.out.WorkbookParserPort;
import com.hmdp.servicedata.application.service.ServiceDataImportApplicationService;
import com.hmdp.servicedata.domain.model.ImportBatch;
import com.hmdp.servicedata.domain.model.ImportBatchStatus;
import com.hmdp.servicedata.domain.model.ImportErrorSeverity;
import com.hmdp.servicedata.domain.model.ScopeRef;
import com.hmdp.servicedata.domain.repository.ImportBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceDataImportApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-27T03:00:00Z");
    private static final String PARSER_VERSION = "competition-workbook-v1";
    private static final ServiceDataImportScope SCOPE =
            new ServiceDataImportScope("tenant-a", "workspace-a", "operator-7");

    private FakeBatchRepository batches;
    private FakeStagingPort staging;
    private FakeWorkbookParser parser;
    private ServiceDataImportApplicationService service;
    private byte[] workbook;

    @BeforeEach
    void setUp() throws IOException {
        batches = new FakeBatchRepository();
        staging = new FakeStagingPort();
        parser = new FakeWorkbookParser(validResult());
        service = applicationService(true, true, 1024 * 1024, 24, NOW);
        workbook = minimalOoxmlWorkbook();
    }

    @Test
    void previewStagesRowsWithoutWritingFactsAndBecomesReadyToConfirm() {
        ServiceDataImportBatchView preview = service.preview(SCOPE, upload(workbook));

        assertThat(preview.getStatus()).isEqualTo("READY_TO_CONFIRM");
        assertThat(preview.isConfirmable()).isTrue();
        assertThat(preview.getVersion()).isEqualTo(1);
        assertThat(preview.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(preview.getCounts().getMessages()).isEqualTo(1);
        assertThat(staging.savedBatchIds).containsExactly(preview.getBatchId());
        assertThat(staging.formalFactWriteCount).isZero();
    }

    @Test
    void blockingErrorsRejectPreviewAndForbidConfirmation() {
        parser.result = resultWithIssues(issues -> issues.add(new ImportIssue(
                "聊天记录", 4, "发送时间", "INVALID_DATETIME",
                ImportErrorSeverity.BLOCKING, "bad***", "无法解析时间值")));

        ServiceDataImportBatchView preview = service.preview(SCOPE, upload(workbook));

        assertThat(preview.getStatus()).isEqualTo("REJECTED");
        assertThat(preview.isConfirmable()).isFalse();
        assertThat(preview.getErrorCount()).isEqualTo(1);
        assertConflict(() -> service.confirm(SCOPE, preview.getBatchId(),
                command(preview, true)));
    }

    @Test
    void warningsRequireExplicitReviewAndSuccessfulConfirmOnlyPreparesCommit() {
        parser.result = resultWithIssues(issues -> issues.add(new ImportIssue(
                "订单", 3, "付款时间", "ANNOTATED_DATETIME",
                ImportErrorSeverity.WARNING, "定金***", "时间值带注记，已按前缀时间解析")));
        ServiceDataImportBatchView preview = service.preview(SCOPE, upload(workbook));

        assertConflict(() -> service.confirm(SCOPE, preview.getBatchId(),
                command(preview, false)));
        ServiceDataImportConfirmationView confirmed = service.confirm(
                SCOPE, preview.getBatchId(), command(preview, true));

        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMING");
        assertThat(confirmed.getVersion()).isEqualTo(2);
        assertThat(confirmed.getCreated().total()).isZero();
        assertThat(confirmed.getUpdated().total()).isZero();
        assertThat(confirmed.getSkipped().total()).isZero();
        assertThat(staging.formalFactWriteCount).isZero();
    }

    @Test
    void hashParserVersionAndBatchVersionMismatchesAreConflicts() {
        ServiceDataImportBatchView preview = service.preview(SCOPE, upload(workbook));

        assertConflict(() -> service.confirm(SCOPE, preview.getBatchId(),
                new ConfirmServiceDataImportCommand("f".repeat(64), preview.getParserVersion(),
                        preview.getVersion(), true)));
        assertConflict(() -> service.confirm(SCOPE, preview.getBatchId(),
                new ConfirmServiceDataImportCommand(preview.getSourceSha256(), "old-parser",
                        preview.getVersion(), true)));
        assertConflict(() -> service.confirm(SCOPE, preview.getBatchId(),
                new ConfirmServiceDataImportCommand(preview.getSourceSha256(),
                        preview.getParserVersion(), preview.getVersion() + 1, true)));
    }

    @Test
    void expiredAndAlreadyConfirmedBatchesAreConflicts() {
        ServiceDataImportBatchView preview = service.preview(SCOPE, upload(workbook));
        ServiceDataImportApplicationService later = applicationService(
                true, true, 1024 * 1024, 24, NOW.plus(Duration.ofHours(25)));

        assertConflict(() -> later.confirm(SCOPE, preview.getBatchId(), command(preview, true)));

        ImportBatch confirmed = new ImportBatch("already-confirmed", SCOPE.toScopeRef(),
                "data.xlsx", "a".repeat(64), PARSER_VERSION, ImportBatchStatus.CONFIRMED,
                0, 0, NOW.plusSeconds(3600), 3);
        batches.insert(confirmed, SCOPE.getActorId());
        assertConflict(() -> service.confirm(SCOPE, confirmed.getId(),
                new ConfirmServiceDataImportCommand(confirmed.getSourceSha256(), PARSER_VERSION,
                        confirmed.getVersion(), true)));
    }

    @Test
    void duplicatePreviewReusesTheValidBatchForTheSameScopeHashAndParser() {
        ServiceDataImportBatchView first = service.preview(SCOPE, upload(workbook));
        ServiceDataImportBatchView second = service.preview(SCOPE, upload(workbook));

        assertThat(second.getBatchId()).isEqualTo(first.getBatchId());
        assertThat(parser.parseCount).isEqualTo(1);
        assertThat(batches.insertCount).isEqualTo(1);
        assertThat(staging.savedBatchIds).containsExactly(first.getBatchId());
    }

    @Test
    void errorReportIsPagedAndContainsOnlyMaskedValues() {
        parser.result = resultWithIssues(issues -> {
            issues.add(new ImportIssue("订单", 2, "账号", "INVALID", ImportErrorSeverity.WARNING,
                    "135***", "账号格式无效"));
            issues.add(new ImportIssue("订单", 3, "数量", "INVALID", ImportErrorSeverity.WARNING,
                    "abc***", "数量格式无效"));
            issues.add(new ImportIssue("订单", 4, "日期", "INVALID", ImportErrorSeverity.BLOCKING,
                    "bad***", "日期格式无效"));
        });
        ServiceDataImportBatchView preview = service.preview(SCOPE, upload(workbook));

        ServiceDataImportErrorPage page = service.getErrors(SCOPE, preview.getBatchId(), 2, 2);

        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().get(0).getMaskedValue()).doesNotContain("13512345678");
        assertThat(page.getItems().get(0).getSeverity()).isIn("WARNING", "BLOCKING");
    }

    @Test
    void crossWorkspaceReadAndConfirmUseTheSameNotFoundSemantics() {
        ServiceDataImportBatchView preview = service.preview(SCOPE, upload(workbook));
        ServiceDataImportScope other =
                new ServiceDataImportScope("tenant-a", "workspace-b", "operator-8");

        assertNotFound(() -> service.getBatch(other, preview.getBatchId()));
        assertNotFound(() -> service.getErrors(other, preview.getBatchId(), 1, 50));
        assertNotFound(() -> service.confirm(other, preview.getBatchId(), command(preview, true)));
    }

    @Test
    void disabledImportFeatureReturnsTheStableFeatureError() {
        ServiceDataImportApplicationService disabled = applicationService(
                true, false, 1024 * 1024, 24, NOW);

        assertThatThrownBy(() -> disabled.preview(SCOPE, upload(workbook)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.CS_FEATURE_DISABLED));
    }

    @Test
    void invalidMimeExtensionSizeAndNonOoxmlContentAreValidationFailures() {
        assertValidation(() -> service.preview(SCOPE,
                new ServiceDataImportUpload("data.xlsx", "text/plain", workbook.length,
                        () -> new ByteArrayInputStream(workbook))));
        assertValidation(() -> service.preview(SCOPE,
                new ServiceDataImportUpload("data.xlsm", xlsxMime(), workbook.length,
                        () -> new ByteArrayInputStream(workbook))));
        assertValidation(() -> service.preview(SCOPE,
                new ServiceDataImportUpload("data.xlsx", xlsxMime(), 1024 * 1024 + 1L,
                        () -> new ByteArrayInputStream(workbook))));
        byte[] junk = "PK-not-an-ooxml-workbook-token=secret".getBytes();
        assertValidation(() -> service.preview(SCOPE,
                new ServiceDataImportUpload("data.xlsx", xlsxMime(), junk.length,
                        () -> new ByteArrayInputStream(junk))));
    }

    @Test
    void actualBytesCannotBypassTheLimitWithAForgedDeclaredSize() {
        ServiceDataImportApplicationService tinyLimit = applicationService(
                true, true, 16, 24, NOW);

        assertValidation(() -> tinyLimit.preview(SCOPE,
                new ServiceDataImportUpload("data.xlsx", xlsxMime(), 1,
                        () -> new ByteArrayInputStream(workbook))));
    }

    @Test
    void uploadFileNamesAreNeverReturnedOrPersistedVerbatim() {
        ServiceDataImportBatchView ordinary = service.preview(SCOPE,
                new ServiceDataImportUpload("customer-complaint.xlsx", xlsxMime(),
                        workbook.length, () -> new ByteArrayInputStream(workbook)));

        assertThat(ordinary.getFileName()).isEqualTo("uploaded-workbook.xlsx");

        batches = new FakeBatchRepository();
        staging = new FakeStagingPort();
        service = applicationService(true, true, 1024 * 1024, 24, NOW);
        ServiceDataImportUpload sensitive = new ServiceDataImportUpload(
                "token=super-secret-13512345678.xlsx", xlsxMime(), workbook.length,
                () -> new ByteArrayInputStream(workbook));

        ServiceDataImportBatchView preview = service.preview(SCOPE, sensitive);

        assertThat(preview.getFileName()).isEqualTo("uploaded-workbook.xlsx");
        assertThat(preview.getFileName()).doesNotContain("secret", "13512345678");
    }

    @Test
    void parserRuntimeFailuresAreSanitizedAsValidationErrors() {
        parser.failure = new IllegalStateException(
                "token=super-secret account=13512345678 raw-row-content");

        assertValidation(() -> service.preview(SCOPE, upload(workbook)));
    }

    private ServiceDataImportApplicationService applicationService(
            boolean customerServiceEnabled, boolean importEnabled, long maxBytes, int ttlHours,
            Instant instant) {
        return new ServiceDataImportApplicationService(batches, staging, parser,
                customerServiceEnabled, importEnabled, maxBytes, ttlHours,
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    private ServiceDataImportUpload upload(byte[] bytes) {
        return new ServiceDataImportUpload("competition.xlsx", xlsxMime(), bytes.length,
                () -> new ByteArrayInputStream(bytes));
    }

    private ConfirmServiceDataImportCommand command(ServiceDataImportBatchView preview,
                                                     boolean warningsReviewed) {
        return new ConfirmServiceDataImportCommand(preview.getSourceSha256(),
                preview.getParserVersion(), preview.getVersion(), warningsReviewed);
    }

    private static String xlsxMime() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    private WorkbookParseResult validResult() {
        WorkbookParseResult result = new WorkbookParseResult(PARSER_VERSION);
        result.getMessages().add(new ImportRows.MessageRow("S00082", 1, "msg-1", NOW,
                "CONSUMER", "方***", "脱敏消息", "TEXT", null, null, 2));
        return result;
    }

    private WorkbookParseResult resultWithIssues(Consumer<List<ImportIssue>> issueWriter) {
        WorkbookParseResult result = validResult();
        issueWriter.accept(result.getIssues());
        return result;
    }

    private static byte[] minimalOoxmlWorkbook() throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("xl/workbook.xml"));
            zip.write("<workbook/>".getBytes());
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        }
    }

    private void assertConflict(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.CS_IMPORT_CONFLICT));
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.CS_RESOURCE_NOT_FOUND));
    }

    private void assertValidation(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CS_IMPORT_VALIDATION_FAILED);
                    assertThat(error.getMessage()).doesNotContain("secret", "token");
                });
    }

    private static final class FakeWorkbookParser implements WorkbookParserPort {
        private WorkbookParseResult result;
        private int parseCount;
        private RuntimeException failure;

        private FakeWorkbookParser(WorkbookParseResult result) {
            this.result = result;
        }

        @Override
        public String parserVersion() {
            return PARSER_VERSION;
        }

        @Override
        public WorkbookParseResult parse(InputStream input, long declaredSize) {
            parseCount++;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static final class FakeBatchRepository implements ImportBatchRepository {
        private final Map<String, ImportBatch> values = new HashMap<>();
        private int insertCount;

        @Override
        public void insert(ImportBatch batch, String actor) {
            values.put(key(batch.getScope(), batch.getId()), batch);
            insertCount++;
        }

        @Override
        public Optional<ImportBatch> findById(ScopeRef scope, String batchId) {
            return Optional.ofNullable(values.get(key(scope, batchId)));
        }

        @Override
        public Optional<ImportBatch> findReusable(ScopeRef scope, String sha, String parserVersion) {
            return values.values().stream()
                    .filter(batch -> batch.getScope().equals(scope))
                    .filter(batch -> batch.getSourceSha256().equals(sha))
                    .filter(batch -> batch.getParserVersion().equals(parserVersion))
                    .filter(batch -> batch.getStatus() == ImportBatchStatus.READY_TO_CONFIRM
                            || batch.getStatus() == ImportBatchStatus.CONFIRMED)
                    .findFirst();
        }

        @Override
        public boolean updateWithVersion(ImportBatch batch, int expectedVersion, String actor) {
            String key = key(batch.getScope(), batch.getId());
            ImportBatch current = values.get(key);
            if (current == null || current.getVersion() != expectedVersion) {
                return false;
            }
            values.put(key, new ImportBatch(batch.getId(), batch.getScope(), batch.getFileName(),
                    batch.getSourceSha256(), batch.getParserVersion(), batch.getStatus(),
                    batch.getWarningCount(), batch.getBlockingErrorCount(),
                    batch.getStagingExpiresAt(), expectedVersion + 1));
            return true;
        }

        private static String key(ScopeRef scope, String id) {
            return scope.getTenantId() + "/" + scope.getWorkspaceId() + "/" + id;
        }
    }

    private static final class FakeStagingPort implements ImportStagingPort {
        private final Map<String, ServiceDataImportCounts> counts = new HashMap<>();
        private final Map<String, List<ImportIssue>> issues = new HashMap<>();
        private final List<String> savedBatchIds = new ArrayList<>();
        private int formalFactWriteCount;

        @Override
        public void savePreview(ScopeRef scope, String batchId, Instant expiresAt,
                                WorkbookParseResult result, ServiceDataImportCounts previewCounts) {
            counts.put(key(scope, batchId), previewCounts);
            issues.put(key(scope, batchId), new ArrayList<>(result.getIssues()));
            savedBatchIds.add(batchId);
        }

        @Override
        public ServiceDataImportCounts findCounts(ScopeRef scope, String batchId) {
            return counts.getOrDefault(key(scope, batchId), ServiceDataImportCounts.empty());
        }

        @Override
        public ServiceDataImportErrorPage findErrors(ScopeRef scope, String batchId,
                                                     int page, int size) {
            List<ImportIssue> all = new ArrayList<>(
                    issues.getOrDefault(key(scope, batchId), List.of()));
            all.sort(Comparator.comparing(ImportIssue::getRowNo));
            int from = Math.min((page - 1) * size, all.size());
            int to = Math.min(from + size, all.size());
            return ServiceDataImportErrorPage.fromIssues(all.subList(from, to),
                    all.size(), page, size);
        }

        private static String key(ScopeRef scope, String id) {
            return scope.getTenantId() + "/" + scope.getWorkspaceId() + "/" + id;
        }
    }
}
