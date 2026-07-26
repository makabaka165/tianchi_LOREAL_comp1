package com.hmdp.servicedata.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Append-only versioned service case (work order) fact. Case numbers are Strings end
 * to end; content changes create a new version row instead of overwriting history.
 */
public final class ServiceCase {
    private final String id;
    private final ScopeRef scope;
    private final String caseNo;
    private final int caseSeq;
    private final String sourceSystem;
    private final String sourceKey;
    private final String caseType;
    private final String caseStatus;
    private final String priority;
    private final String orderNo;
    private final Instant openedAt;
    private final Instant closedAt;
    private final String description;
    private final String resolution;
    private final int detailSchemaVersion;
    private final String detailJson;
    private final String contentHash;
    private final String importBatchId;

    public ServiceCase(String id, ScopeRef scope, String caseNo, int caseSeq,
                       String sourceSystem, String sourceKey, String caseType,
                       String caseStatus, String priority, String orderNo, Instant openedAt,
                       Instant closedAt, String description, String resolution,
                       int detailSchemaVersion, String detailJson, String contentHash,
                       String importBatchId) {
        this.id = ScopeRef.requireText(id, "id");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.caseNo = ScopeRef.requireText(caseNo, "caseNo");
        if (caseSeq < 1) {
            throw new IllegalArgumentException("caseSeq starts at 1");
        }
        this.caseSeq = caseSeq;
        this.sourceSystem = ScopeRef.requireText(sourceSystem, "sourceSystem");
        this.sourceKey = sourceKey;
        this.caseType = caseType;
        this.caseStatus = caseStatus;
        this.priority = priority;
        this.orderNo = orderNo;
        this.openedAt = openedAt;
        this.closedAt = closedAt;
        this.description = description;
        this.resolution = resolution;
        if (detailSchemaVersion < 1) {
            throw new IllegalArgumentException("detailSchemaVersion starts at 1");
        }
        this.detailSchemaVersion = detailSchemaVersion;
        this.detailJson = detailJson;
        this.contentHash = ImportBatch.requireSha256(contentHash);
        this.importBatchId = importBatchId;
    }

    public String getId() {
        return id;
    }

    public ScopeRef getScope() {
        return scope;
    }

    public String getCaseNo() {
        return caseNo;
    }

    public int getCaseSeq() {
        return caseSeq;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public String getCaseType() {
        return caseType;
    }

    public String getCaseStatus() {
        return caseStatus;
    }

    public String getPriority() {
        return priority;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getDescription() {
        return description;
    }

    public String getResolution() {
        return resolution;
    }

    public int getDetailSchemaVersion() {
        return detailSchemaVersion;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getImportBatchId() {
        return importBatchId;
    }
}
