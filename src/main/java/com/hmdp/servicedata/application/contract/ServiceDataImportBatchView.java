package com.hmdp.servicedata.application.contract;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.Objects;

/** Safe batch summary returned by preview and status queries. */
public final class ServiceDataImportBatchView {
    private final String batchId;
    private final String fileName;
    private final String sourceSha256;
    private final String parserVersion;
    private final ServiceDataImportCounts counts;
    private final int warningCount;
    private final int errorCount;
    private final int blockingErrorCount;
    private final boolean confirmable;
    private final String status;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Instant expiresAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Instant confirmedAt;
    private final int version;

    public ServiceDataImportBatchView(String batchId, String fileName, String sourceSha256,
                                      String parserVersion, ServiceDataImportCounts counts,
                                      int warningCount, int errorCount, boolean confirmable,
                                      String status, Instant expiresAt, Instant confirmedAt,
                                      int version) {
        this.batchId = Objects.requireNonNull(batchId, "batchId");
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.sourceSha256 = Objects.requireNonNull(sourceSha256, "sourceSha256");
        this.parserVersion = Objects.requireNonNull(parserVersion, "parserVersion");
        this.counts = Objects.requireNonNull(counts, "counts");
        this.warningCount = warningCount;
        this.errorCount = errorCount;
        this.blockingErrorCount = errorCount;
        this.confirmable = confirmable;
        this.status = Objects.requireNonNull(status, "status");
        this.expiresAt = expiresAt;
        this.confirmedAt = confirmedAt;
        this.version = version;
    }

    public String getBatchId() {
        return batchId;
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

    public ServiceDataImportCounts getCounts() {
        return counts;
    }

    public int getWarningCount() {
        return warningCount;
    }

    /** Alias required by DATA-003; equals the blocking error count. */
    public int getErrorCount() {
        return errorCount;
    }

    public int getBlockingErrorCount() {
        return blockingErrorCount;
    }

    public boolean isConfirmable() {
        return confirmable;
    }

    public String getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public int getVersion() {
        return version;
    }
}
