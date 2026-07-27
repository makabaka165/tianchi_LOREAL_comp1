package com.hmdp.servicedata.application.contract;

import java.util.Objects;

/** DATA-003 confirmation result. Fact counts stay zero until DATA-004 commits staging. */
public final class ServiceDataImportConfirmationView {
    private final String batchId;
    private final String status;
    private final int version;
    private final ServiceDataImportCounts created;
    private final ServiceDataImportCounts updated;
    private final ServiceDataImportCounts skipped;

    public ServiceDataImportConfirmationView(String batchId, String status, int version,
                                             ServiceDataImportCounts created,
                                             ServiceDataImportCounts updated,
                                             ServiceDataImportCounts skipped) {
        this.batchId = Objects.requireNonNull(batchId, "batchId");
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
        this.created = Objects.requireNonNull(created, "created");
        this.updated = Objects.requireNonNull(updated, "updated");
        this.skipped = Objects.requireNonNull(skipped, "skipped");
    }

    public String getBatchId() {
        return batchId;
    }

    public String getStatus() {
        return status;
    }

    public int getVersion() {
        return version;
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
}
