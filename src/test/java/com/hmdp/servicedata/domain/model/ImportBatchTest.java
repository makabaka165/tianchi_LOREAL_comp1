package com.hmdp.servicedata.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportBatchTest {
    private static final String SHA = "b5ac027e863c5580dab39c8f459e4698d65e9fbec29832c9915448f2087307b7";
    private static final ScopeRef SCOPE = new ScopeRef("default", "default");
    private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-07-27T12:00:00Z");

    private ImportBatch previewing() {
        return ImportBatch.startPreview("batch-1", SCOPE, "data.xlsx", SHA, "workbook-v1", LATER);
    }

    private ImportBatch readyToConfirm(int warnings) {
        ImportBatch batch = previewing();
        batch.finishPreview(warnings, 0);
        return batch;
    }

    @Test
    void previewFinishesReadyWhenNoBlockingErrors() {
        ImportBatch batch = readyToConfirm(2);
        assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.READY_TO_CONFIRM);
        assertThat(batch.isConfirmable(NOW)).isTrue();
    }

    @Test
    void previewFinishesRejectedOnBlockingErrors() {
        ImportBatch batch = previewing();
        batch.finishPreview(0, 3);
        assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.REJECTED);
        assertThat(batch.isConfirmable(NOW)).isFalse();
        assertThat(ImportBatchStatus.REJECTED.isTerminal()).isTrue();
    }

    @Test
    void confirmHappyPathReachesConfirmed() {
        ImportBatch batch = readyToConfirm(0);
        batch.beginConfirm(SHA, "workbook-v1", false, NOW);
        assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.CONFIRMING);
        batch.completeConfirm("user-1", NOW);
        assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.CONFIRMED);
        assertThat(batch.getConfirmedBy()).isEqualTo("user-1");
    }

    @Test
    void confirmRejectsHashMismatch() {
        ImportBatch batch = readyToConfirm(0);
        assertThatThrownBy(() -> batch.beginConfirm(
                "0000000000000000000000000000000000000000000000000000000000000000",
                "workbook-v1", false, NOW))
                .isInstanceOf(ImportConflictException.class)
                .hasMessageContaining("hash");
    }

    @Test
    void confirmRejectsParserVersionMismatch() {
        ImportBatch batch = readyToConfirm(0);
        assertThatThrownBy(() -> batch.beginConfirm(SHA, "workbook-v2", false, NOW))
                .isInstanceOf(ImportConflictException.class)
                .hasMessageContaining("parser");
    }

    @Test
    void confirmAfterStagingTtlExpiresBatch() {
        ImportBatch batch = readyToConfirm(0);
        Instant afterTtl = LATER.plusSeconds(1);
        assertThatThrownBy(() -> batch.beginConfirm(SHA, "workbook-v1", false, afterTtl))
                .isInstanceOf(ImportConflictException.class)
                .hasMessageContaining("expired");
        assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.EXPIRED);
    }

    @Test
    void confirmRequiresWarningReviewWhenWarningsExist() {
        ImportBatch batch = readyToConfirm(4);
        assertThatThrownBy(() -> batch.beginConfirm(SHA, "workbook-v1", false, NOW))
                .isInstanceOf(ImportConflictException.class)
                .hasMessageContaining("warnings");
        batch.beginConfirm(SHA, "workbook-v1", true, NOW);
        assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.CONFIRMING);
    }

    @Test
    void secondConfirmerConflictsWhileConfirming() {
        ImportBatch batch = readyToConfirm(0);
        batch.beginConfirm(SHA, "workbook-v1", false, NOW);
        assertThatThrownBy(() -> batch.beginConfirm(SHA, "workbook-v1", false, NOW))
                .isInstanceOf(ImportConflictException.class)
                .hasMessageContaining("not confirmable");
    }

    @Test
    void failedCommitCanRevertToReadyForRetry() {
        ImportBatch batch = readyToConfirm(0);
        batch.beginConfirm(SHA, "workbook-v1", false, NOW);
        batch.revertToReady();
        assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.READY_TO_CONFIRM);
        assertThat(batch.isConfirmable(NOW)).isTrue();
    }

    @Test
    void terminalStatesRejectFurtherTransitions() {
        ImportBatch batch = readyToConfirm(0);
        batch.beginConfirm(SHA, "workbook-v1", false, NOW);
        batch.completeConfirm("user-1", NOW);
        assertThatThrownBy(batch::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMalformedSha256() {
        assertThatThrownBy(() -> ImportBatch.startPreview(
                "batch-2", SCOPE, "data.xlsx", "not-a-hash", "workbook-v1", LATER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
