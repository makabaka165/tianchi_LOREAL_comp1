package com.hmdp.servicedata.application.service;

import com.hmdp.common.ErrorCode;
import com.hmdp.exception.BusinessException;
import com.hmdp.servicedata.application.contract.ConfirmServiceDataImportCommand;
import com.hmdp.servicedata.application.contract.ServiceDataImportBatchView;
import com.hmdp.servicedata.application.contract.ServiceDataImportCommitSummary;
import com.hmdp.servicedata.application.contract.ServiceDataImportConfirmationView;
import com.hmdp.servicedata.application.contract.ServiceDataImportCounts;
import com.hmdp.servicedata.application.contract.ServiceDataImportErrorPage;
import com.hmdp.servicedata.application.contract.ServiceDataImportScope;
import com.hmdp.servicedata.application.contract.ServiceDataImportUpload;
import com.hmdp.servicedata.application.imports.ImportIssue;
import com.hmdp.servicedata.application.imports.WorkbookParseResult;
import com.hmdp.servicedata.application.event.ServiceFactsImported;
import com.hmdp.servicedata.application.port.in.CommitStagedServiceDataImportUseCase;
import com.hmdp.servicedata.application.port.in.ConfirmServiceDataImportUseCase;
import com.hmdp.servicedata.application.port.in.GetServiceDataImportBatchUseCase;
import com.hmdp.servicedata.application.port.in.GetServiceDataImportErrorsUseCase;
import com.hmdp.servicedata.application.port.in.PreviewServiceDataImportUseCase;
import com.hmdp.servicedata.application.port.out.ImportStagingPort;
import com.hmdp.servicedata.application.port.out.ServiceFactsImportedEventPublisher;
import com.hmdp.servicedata.application.port.out.WorkbookParserPort;
import com.hmdp.servicedata.domain.model.ImportBatch;
import com.hmdp.servicedata.domain.model.ImportBatchStatus;
import com.hmdp.servicedata.domain.model.ImportConflictException;
import com.hmdp.servicedata.domain.model.ImportErrorSeverity;
import com.hmdp.servicedata.domain.model.ScopeRef;
import com.hmdp.servicedata.domain.repository.ImportBatchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/** Coordinates scoped workbook preview and atomic staging-to-fact confirmation. */
@Service
public class ServiceDataImportApplicationService implements PreviewServiceDataImportUseCase,
        GetServiceDataImportBatchUseCase, GetServiceDataImportErrorsUseCase,
        ConfirmServiceDataImportUseCase {
    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final Set<String> ALLOWED_MEDIA_TYPES =
            Set.of(XLSX_MEDIA_TYPE, "application/octet-stream");
    private static final int MAX_ZIP_ENTRIES_TO_INSPECT = 512;

    private final ImportBatchRepository batches;
    private final ImportStagingPort staging;
    private final WorkbookParserPort parser;
    private final CommitStagedServiceDataImportUseCase committer;
    private final ServiceFactsImportedEventPublisher events;
    private final boolean enabled;
    private final long maxFileSizeBytes;
    private final Duration stagingTtl;
    private final Clock clock;

    @Autowired
    public ServiceDataImportApplicationService(
            ImportBatchRepository batches,
            ImportStagingPort staging,
            WorkbookParserPort parser,
            CommitStagedServiceDataImportUseCase committer,
            ServiceFactsImportedEventPublisher events,
            @Value("${hmdp.customer-service.enabled:false}") boolean customerServiceEnabled,
            @Value("${hmdp.customer-service.import.enabled:false}") boolean importEnabled,
            @Value("${hmdp.customer-service.import.max-file-size-bytes:20971520}")
            long maxFileSizeBytes,
            @Value("${hmdp.customer-service.import.staging-ttl-hours:24}") int stagingTtlHours) {
        this(batches, staging, parser, committer, events, customerServiceEnabled, importEnabled,
                maxFileSizeBytes, stagingTtlHours, Clock.systemUTC());
    }

    public ServiceDataImportApplicationService(
            ImportBatchRepository batches,
            ImportStagingPort staging,
            WorkbookParserPort parser,
            CommitStagedServiceDataImportUseCase committer,
            ServiceFactsImportedEventPublisher events,
            boolean customerServiceEnabled,
            boolean importEnabled,
            long maxFileSizeBytes,
            int stagingTtlHours,
            Clock clock) {
        this.batches = Objects.requireNonNull(batches, "batches");
        this.staging = Objects.requireNonNull(staging, "staging");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.committer = Objects.requireNonNull(committer, "committer");
        this.events = Objects.requireNonNull(events, "events");
        this.enabled = customerServiceEnabled && importEnabled;
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("maxFileSizeBytes must be positive");
        }
        if (stagingTtlHours <= 0) {
            throw new IllegalArgumentException("stagingTtlHours must be positive");
        }
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.stagingTtl = Duration.ofHours(stagingTtlHours);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public ServiceDataImportBatchView preview(ServiceDataImportScope requestScope,
                                              ServiceDataImportUpload upload) {
        requireEnabled();
        ServiceDataImportScope scope = Objects.requireNonNull(requestScope, "scope");
        ServiceDataImportUpload source = Objects.requireNonNull(upload, "upload");
        String fileName = validateUploadMetadata(source);
        String sourceSha256 = calculateSha256(source);
        validateOoxmlSignature(source);
        ScopeRef scopeRef = scope.toScopeRef();
        Instant now = clock.instant();

        OptionalBatch reusable = reusable(scopeRef, sourceSha256, now);
        if (reusable.batch != null) {
            return toView(reusable.batch, now);
        }

        Instant expiresAt = now.plus(stagingTtl);
        ImportBatch batch = ImportBatch.startPreview(UUID.randomUUID().toString(), scopeRef,
                fileName, sourceSha256, parser.parserVersion(), expiresAt);
        batches.insert(batch, scope.getActorId());

        WorkbookParseResult result = parse(source);
        if (!parser.parserVersion().equals(result.getParserVersion())) {
            throw validationFailure();
        }
        addUnknownColumnWarnings(result);
        ServiceDataImportCounts counts = ServiceDataImportCounts.from(result);
        staging.savePreview(scopeRef, batch.getId(), expiresAt, result, counts);
        batch.finishPreview(Math.toIntExact(result.warningIssueCount()),
                Math.toIntExact(result.blockingIssueCount()));
        if (!batches.updateWithVersion(batch, batch.getVersion(), scope.getActorId())) {
            throw conflict();
        }
        return toView(requireBatch(scopeRef, batch.getId()), now);
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceDataImportBatchView getBatch(ServiceDataImportScope requestScope,
                                               String batchId) {
        requireEnabled();
        ServiceDataImportScope scope = Objects.requireNonNull(requestScope, "scope");
        return toView(requireBatch(scope.toScopeRef(), batchId), clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceDataImportErrorPage getErrors(ServiceDataImportScope requestScope,
                                                String batchId, int page, int size) {
        requireEnabled();
        ServiceDataImportScope scope = Objects.requireNonNull(requestScope, "scope");
        if (page < 1 || size < 1 || size > 200) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "invalid import error page");
        }
        ScopeRef scopeRef = scope.toScopeRef();
        requireBatch(scopeRef, batchId);
        return staging.findErrors(scopeRef, batchId, page, size);
    }

    @Override
    @Transactional
    public ServiceDataImportConfirmationView confirm(ServiceDataImportScope requestScope,
                                                     String batchId,
                                                     ConfirmServiceDataImportCommand command) {
        requireEnabled();
        ServiceDataImportScope scope = Objects.requireNonNull(requestScope, "scope");
        ConfirmServiceDataImportCommand expected = Objects.requireNonNull(command, "command");
        ScopeRef scopeRef = scope.toScopeRef();
        ImportBatch batch = requireBatch(scopeRef, batchId);
        Instant now = clock.instant();
        try {
            batch.beginConfirm(expected.getExpectedSourceSha256(),
                    expected.getExpectedParserVersion(), expected.getExpectedVersion(),
                    expected.isWarningsReviewed(), now);
        } catch (ImportConflictException | IllegalStateException e) {
            throw conflict();
        }
        if (!batches.updateWithVersion(batch, expected.getExpectedVersion(), scope.getActorId())) {
            throw conflict();
        }
        ImportBatch prepared = requireBatch(scopeRef, batchId);
        ServiceDataImportCommitSummary summary;
        try {
            summary = committer.commit(scopeRef, batchId, scope.getActorId());
        } catch (ServiceDataImportCommitException e) {
            throw conflict();
        }
        prepared.completeConfirm(scope.getActorId(), now);
        if (!batches.updateWithVersion(prepared, prepared.getVersion(), scope.getActorId())) {
            throw conflict();
        }
        ImportBatch confirmed = requireBatch(scopeRef, batchId);
        staging.completeCommit(scopeRef, batchId, summary);
        events.publishAfterCommit(new ServiceFactsImported(batchId, scopeRef.getTenantId(),
                scopeRef.getWorkspaceId(), summary.getCreated(), summary.getUpdated(),
                summary.getSkipped(), now));
        return new ServiceDataImportConfirmationView(confirmed.getId(),
                confirmed.getStatus().name(), confirmed.getVersion(), summary.getCreated(),
                summary.getUpdated(), summary.getSkipped());
    }

    private OptionalBatch reusable(ScopeRef scope, String sourceSha256, Instant now) {
        ImportBatch batch = batches.findReusable(scope, sourceSha256, parser.parserVersion())
                .orElse(null);
        if (batch == null) {
            return OptionalBatch.empty();
        }
        if (batch.getStatus() == ImportBatchStatus.CONFIRMED || batch.isConfirmable(now)) {
            return OptionalBatch.of(batch);
        }
        return OptionalBatch.empty();
    }

    private ServiceDataImportBatchView toView(ImportBatch batch, Instant now) {
        ServiceDataImportCounts counts = staging.findCounts(batch.getScope(), batch.getId());
        return new ServiceDataImportBatchView(batch.getId(), batch.getFileName(),
                batch.getSourceSha256(), batch.getParserVersion(), counts,
                batch.getWarningCount(), batch.getBlockingErrorCount(),
                batch.isConfirmable(now), batch.getStatus().name(),
                batch.getStagingExpiresAt(), batch.getConfirmedAt(), batch.getVersion());
    }

    private ImportBatch requireBatch(ScopeRef scope, String batchId) {
        String safeId = batchId == null ? "" : batchId.trim();
        if (safeId.isEmpty() || safeId.length() > 64) {
            throw notFound();
        }
        return batches.findById(scope, safeId).orElseThrow(this::notFound);
    }

    private String validateUploadMetadata(ServiceDataImportUpload upload) {
        String suppliedName = upload.getFileName();
        if (suppliedName == null) {
            throw validationFailure();
        }
        String normalizedPath = suppliedName.replace('\\', '/');
        String fileName = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1).trim();
        String mediaType = upload.getContentType() == null ? ""
                : upload.getContentType().trim().toLowerCase(Locale.ROOT);
        if (fileName.isEmpty() || fileName.length() > 255
                || fileName.chars().anyMatch(Character::isISOControl)
                || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                || !ALLOWED_MEDIA_TYPES.contains(mediaType)
                || upload.getDeclaredSize() <= 0
                || upload.getDeclaredSize() > maxFileSizeBytes) {
            throw validationFailure();
        }
        // Original names are untrusted display data and may contain account or consumer PII.
        return "uploaded-workbook.xlsx";
    }

    private String calculateSha256(ServiceDataImportUpload upload) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        long count = 0;
        byte[] buffer = new byte[8192];
        try (InputStream input = new BufferedInputStream(upload.openStream())) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                count += read;
                if (count > maxFileSizeBytes) {
                    throw validationFailure();
                }
                digest.update(buffer, 0, read);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw validationFailure();
        }
        if (count == 0) {
            throw validationFailure();
        }
        return toLowerHex(digest.digest());
    }

    private void validateOoxmlSignature(ServiceDataImportUpload upload) {
        boolean contentTypes = false;
        boolean workbook = false;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(
                new BufferedInputStream(upload.openStream()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null && entries++ < MAX_ZIP_ENTRIES_TO_INSPECT) {
                String name = entry.getName();
                contentTypes |= "[Content_Types].xml".equals(name);
                workbook |= "xl/workbook.xml".equals(name);
                if (contentTypes && workbook) {
                    break;
                }
            }
        } catch (ZipException e) {
            throw validationFailure();
        } catch (IOException | RuntimeException e) {
            throw validationFailure();
        }
        if (!contentTypes || !workbook) {
            throw validationFailure();
        }
    }

    private WorkbookParseResult parse(ServiceDataImportUpload upload) {
        try (InputStream input = new BufferedInputStream(upload.openStream())) {
            return parser.parse(input, upload.getDeclaredSize());
        } catch (IOException | RuntimeException e) {
            throw validationFailure();
        }
    }

    private void addUnknownColumnWarnings(WorkbookParseResult result) {
        for (String unknown : result.getUnknownColumns()) {
            int delimiter = unknown.indexOf('!');
            String sheet = delimiter > 0 ? unknown.substring(0, delimiter) : "workbook";
            result.getIssues().add(new ImportIssue(sheet, 1, null, "UNKNOWN_COLUMN",
                    ImportErrorSeverity.WARNING, null, "未知列已忽略"));
        }
    }

    private static String toLowerHex(byte[] bytes) {
        char[] alphabet = "0123456789abcdef".toCharArray();
        char[] encoded = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            encoded[i * 2] = alphabet[value >>> 4];
            encoded[i * 2 + 1] = alphabet[value & 0x0f];
        }
        return new String(encoded);
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new BusinessException(ErrorCode.CS_FEATURE_DISABLED,
                    ErrorCode.CS_FEATURE_DISABLED.getMessage());
        }
    }

    private BusinessException validationFailure() {
        return new BusinessException(ErrorCode.CS_IMPORT_VALIDATION_FAILED,
                "workbook upload validation failed");
    }

    private BusinessException conflict() {
        return new BusinessException(ErrorCode.CS_IMPORT_CONFLICT,
                ErrorCode.CS_IMPORT_CONFLICT.getMessage());
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.CS_RESOURCE_NOT_FOUND,
                ErrorCode.CS_RESOURCE_NOT_FOUND.getMessage());
    }

    private static final class OptionalBatch {
        private final ImportBatch batch;

        private OptionalBatch(ImportBatch batch) {
            this.batch = batch;
        }

        private static OptionalBatch of(ImportBatch batch) {
            return new OptionalBatch(batch);
        }

        private static OptionalBatch empty() {
            return new OptionalBatch(null);
        }
    }
}
