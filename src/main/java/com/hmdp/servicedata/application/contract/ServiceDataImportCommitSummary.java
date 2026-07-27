package com.hmdp.servicedata.application.contract;

import java.util.Objects;

/** Typed outcome of one atomic staging-to-fact commit. */
public final class ServiceDataImportCommitSummary {
    private final ServiceDataImportCounts created;
    private final ServiceDataImportCounts updated;
    private final ServiceDataImportCounts skipped;

    public ServiceDataImportCommitSummary(ServiceDataImportCounts created,
                                          ServiceDataImportCounts updated,
                                          ServiceDataImportCounts skipped) {
        this.created = Objects.requireNonNull(created, "created");
        this.updated = Objects.requireNonNull(updated, "updated");
        this.skipped = Objects.requireNonNull(skipped, "skipped");
    }

    public static ServiceDataImportCommitSummary empty() {
        ServiceDataImportCounts zero = ServiceDataImportCounts.empty();
        return new ServiceDataImportCommitSummary(zero, zero, zero);
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
