package com.hmdp.servicedata.domain.repository;

import com.hmdp.servicedata.domain.model.ImportBatch;
import com.hmdp.servicedata.domain.model.ScopeRef;

import java.util.Optional;

/** Persistence port for import batches. All reads are scope-checked. */
public interface ImportBatchRepository {

    void insert(ImportBatch batch);

    Optional<ImportBatch> findById(ScopeRef scope, String batchId);

    /**
     * A completed preview of the same file with the same parser version may be reused
     * instead of re-staging (READY_TO_CONFIRM or CONFIRMED, newest first).
     */
    Optional<ImportBatch> findReusable(ScopeRef scope, String sourceSha256, String parserVersion);

    /**
     * Optimistic update: persists current state only when the stored version equals
     * {@code expectedVersion}; increments the version on success.
     *
     * @return false when another writer already moved the batch
     */
    boolean updateWithVersion(ImportBatch batch, int expectedVersion);
}
