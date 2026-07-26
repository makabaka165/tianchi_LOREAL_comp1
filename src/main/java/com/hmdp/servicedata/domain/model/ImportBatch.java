package com.hmdp.servicedata.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Aggregate controlling the preview/confirm import lifecycle. Confirmation demands the
 * exact source hash and parser version the operator previewed, zero blocking errors,
 * an unexpired staging window and an explicit warning review when warnings exist.
 */
public final class ImportBatch {
    private final String id;
    private final ScopeRef scope;
    private final String fileName;
    private final String sourceSha256;
    private final String parserVersion;
    private ImportBatchStatus status;
    private int warningCount;
    private int blockingErrorCount;
    private Instant stagingExpiresAt;
    private Instant confirmedAt;
    private String confirmedBy;
    private int version;

    public ImportBatch(String id, ScopeRef scope, String fileName, String sourceSha256,
                       String parserVersion, ImportBatchStatus status, int warningCount,
                       int blockingErrorCount, Instant stagingExpiresAt, int version) {
        this.id = ScopeRef.requireText(id, "id");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.fileName = ScopeRef.requireText(fileName, "fileName");
        this.sourceSha256 = requireSha256(sourceSha256);
        this.parserVersion = ScopeRef.requireText(parserVersion, "parserVersion");
        this.status = Objects.requireNonNull(status, "status");
        this.warningCount = requireNonNegative(warningCount, "warningCount");
        this.blockingErrorCount = requireNonNegative(blockingErrorCount, "blockingErrorCount");
        this.stagingExpiresAt = stagingExpiresAt;
        this.version = requireNonNegative(version, "version");
    }

    public static ImportBatch startPreview(String id, ScopeRef scope, String fileName,
                                           String sourceSha256, String parserVersion,
                                           Instant stagingExpiresAt) {
        return new ImportBatch(id, scope, fileName, sourceSha256, parserVersion,
                ImportBatchStatus.PREVIEWING, 0, 0, stagingExpiresAt, 0);
    }

    static String requireSha256(String value) {
        String trimmed = ScopeRef.requireText(value, "sourceSha256").toLowerCase(Locale.ROOT);
        if (!trimmed.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sourceSha256 must be a 64-char hex digest");
        }
        return trimmed;
    }

    private static int requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }

    private void transitionTo(ImportBatchStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "illegal import batch transition " + status + " -> " + target);
        }
        this.status = target;
    }

    public void finishPreview(int warnings, int blockingErrors) {
        this.warningCount = requireNonNegative(warnings, "warnings");
        this.blockingErrorCount = requireNonNegative(blockingErrors, "blockingErrors");
        transitionTo(blockingErrors > 0 ? ImportBatchStatus.REJECTED
                : ImportBatchStatus.READY_TO_CONFIRM);
    }

    public boolean isConfirmable(Instant now) {
        return status == ImportBatchStatus.READY_TO_CONFIRM
                && blockingErrorCount == 0
                && !isStagingExpired(now);
    }

    public boolean isStagingExpired(Instant now) {
        return stagingExpiresAt != null && now.isAfter(stagingExpiresAt);
    }

    /**
     * @throws ImportConflictException when the operator's expectations no longer match
     *         this batch (stale hash/parser/status/TTL) — mapped to CS_IMPORT_CONFLICT.
     */
    public void beginConfirm(String expectedSha256, String expectedParserVersion,
                             boolean warningsReviewed, Instant now) {
        if (!sourceSha256.equalsIgnoreCase(expectedSha256 == null ? "" : expectedSha256.trim())) {
            throw new ImportConflictException("source hash mismatch");
        }
        if (!parserVersion.equals(expectedParserVersion == null ? "" : expectedParserVersion.trim())) {
            throw new ImportConflictException("parser version mismatch");
        }
        if (isStagingExpired(now)) {
            transitionToExpiredIfPossible();
            throw new ImportConflictException("staging expired");
        }
        if (status != ImportBatchStatus.READY_TO_CONFIRM) {
            throw new ImportConflictException("batch is not confirmable in status " + status);
        }
        if (blockingErrorCount > 0) {
            throw new ImportConflictException("batch has blocking errors");
        }
        if (warningCount > 0 && !warningsReviewed) {
            throw new ImportConflictException("warnings must be reviewed before confirmation");
        }
        transitionTo(ImportBatchStatus.CONFIRMING);
    }

    private void transitionToExpiredIfPossible() {
        if (status.canTransitionTo(ImportBatchStatus.EXPIRED)) {
            status = ImportBatchStatus.EXPIRED;
        }
    }

    public void completeConfirm(String actor, Instant now) {
        transitionTo(ImportBatchStatus.CONFIRMED);
        this.confirmedAt = Objects.requireNonNull(now, "now");
        this.confirmedBy = ScopeRef.requireText(actor, "actor");
    }

    /** Commit failed after CONFIRMING: return to READY_TO_CONFIRM for a clean retry. */
    public void revertToReady() {
        transitionTo(ImportBatchStatus.READY_TO_CONFIRM);
    }

    public void cancel() {
        transitionTo(ImportBatchStatus.CANCELLED);
    }

    public String getId() {
        return id;
    }

    public ScopeRef getScope() {
        return scope;
    }

    public String getFileName() {
        return fileName;
    }

    public String getSourceSha256() {
        return sourceSha256;
    }

    public String getParserVersion() {
        return parserVersion;
    }

    public ImportBatchStatus getStatus() {
        return status;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public int getBlockingErrorCount() {
        return blockingErrorCount;
    }

    public Instant getStagingExpiresAt() {
        return stagingExpiresAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public int getVersion() {
        return version;
    }
}
