package com.hmdp.servicedata.domain.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Lifecycle of an import batch; facts are only written from CONFIRMING. */
public enum ImportBatchStatus {
    PREVIEWING,
    READY_TO_CONFIRM,
    REJECTED,
    CONFIRMING,
    CONFIRMED,
    CANCELLED,
    EXPIRED;

    private static Set<ImportBatchStatus> next(ImportBatchStatus status) {
        switch (status) {
            case PREVIEWING:
                return EnumSet.of(READY_TO_CONFIRM, REJECTED, CANCELLED);
            case READY_TO_CONFIRM:
                return EnumSet.of(CONFIRMING, CANCELLED, EXPIRED);
            case CONFIRMING:
                return EnumSet.of(CONFIRMED, READY_TO_CONFIRM);
            default:
                return Collections.emptySet();
        }
    }

    public boolean canTransitionTo(ImportBatchStatus target) {
        return next(this).contains(target);
    }

    public boolean isTerminal() {
        return next(this).isEmpty();
    }
}
