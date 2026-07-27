package com.hmdp.servicedata.application.event;

import com.hmdp.servicedata.application.contract.ServiceDataImportCounts;

import java.time.Instant;
import java.util.Objects;

/** PII-free application event emitted after imported facts have committed. */
public final class ServiceFactsImported {
    private final String batchId;
    private final String tenantId;
    private final String workspaceId;
    private final ServiceDataImportCounts created;
    private final ServiceDataImportCounts updated;
    private final ServiceDataImportCounts skipped;
    private final Instant occurredAt;

    public ServiceFactsImported(String batchId, String tenantId, String workspaceId,
                                ServiceDataImportCounts created,
                                ServiceDataImportCounts updated,
                                ServiceDataImportCounts skipped, Instant occurredAt) {
        this.batchId = Objects.requireNonNull(batchId, "batchId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.created = Objects.requireNonNull(created, "created");
        this.updated = Objects.requireNonNull(updated, "updated");
        this.skipped = Objects.requireNonNull(skipped, "skipped");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public String getBatchId() {
        return batchId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public ServiceDataImportCounts getCreated() {
        return created;
    }

    public ServiceDataImportCounts getUpdated() {
        return updated;
    }

    public ServiceDataImportCounts getSkipped() {
        return skipped;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
