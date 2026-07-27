package com.hmdp.servicedata.infrastructure.persistence;

import com.hmdp.servicedata.domain.model.ImportBatch;
import com.hmdp.servicedata.domain.model.ImportBatchStatus;
import com.hmdp.servicedata.domain.model.ScopeRef;
import com.hmdp.servicedata.domain.repository.ImportBatchRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcImportBatchRepository implements ImportBatchRepository {
    private final JdbcTemplate jdbc;

    public JdbcImportBatchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<ImportBatch> MAPPER = (rs, rowNum) -> new ImportBatch(
            rs.getString("id"),
            new ScopeRef(rs.getString("tenant_id"), rs.getString("workspace_id")),
            rs.getString("file_name"),
            rs.getString("source_sha256"),
            rs.getString("parser_version"),
            ImportBatchStatus.valueOf(rs.getString("status")),
            rs.getInt("warning_count"),
            rs.getInt("blocking_error_count"),
            toInstant(rs.getTimestamp("staging_expires_at")),
            toInstant(rs.getTimestamp("confirmed_at")),
            rs.getString("confirmed_by"),
            rs.getInt("version"));

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    @Override
    public void insert(ImportBatch batch, String actor) {
        String auditedActor = ScopeRef.requireText(actor, "actor");
        jdbc.update(
                "insert into cs_data_import_batch (id, tenant_id, workspace_id, file_name, "
                        + "source_sha256, parser_version, status, warning_count, "
                        + "blocking_error_count, staging_expires_at, created_by, updated_by, version) "
                        + "values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                batch.getId(),
                batch.getScope().getTenantId(),
                batch.getScope().getWorkspaceId(),
                batch.getFileName(),
                batch.getSourceSha256(),
                batch.getParserVersion(),
                batch.getStatus().name(),
                batch.getWarningCount(),
                batch.getBlockingErrorCount(),
                toTimestamp(batch.getStagingExpiresAt()),
                auditedActor,
                auditedActor,
                batch.getVersion());
    }

    @Override
    public Optional<ImportBatch> findById(ScopeRef scope, String batchId) {
        List<ImportBatch> rows = jdbc.query(
                "select id, tenant_id, workspace_id, file_name, source_sha256, parser_version, "
                        + "status, warning_count, blocking_error_count, staging_expires_at, "
                        + "confirmed_at, confirmed_by, version "
                        + "from cs_data_import_batch "
                        + "where id = ? and tenant_id = ? and workspace_id = ?",
                MAPPER, batchId, scope.getTenantId(), scope.getWorkspaceId());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Optional<ImportBatch> findReusable(ScopeRef scope, String sourceSha256, String parserVersion) {
        List<ImportBatch> rows = jdbc.query(
                "select id, tenant_id, workspace_id, file_name, source_sha256, parser_version, "
                        + "status, warning_count, blocking_error_count, staging_expires_at, "
                        + "confirmed_at, confirmed_by, version "
                        + "from cs_data_import_batch "
                        + "where tenant_id = ? and workspace_id = ? and source_sha256 = ? "
                        + "and parser_version = ? and (status = 'CONFIRMED' or "
                        + "(status = 'READY_TO_CONFIRM' and (staging_expires_at is null "
                        + "or staging_expires_at > current_timestamp(3)))) "
                        + "order by created_at desc limit 1",
                MAPPER, scope.getTenantId(), scope.getWorkspaceId(),
                sourceSha256, parserVersion);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public boolean updateWithVersion(ImportBatch batch, int expectedVersion, String actor) {
        String auditedActor = ScopeRef.requireText(actor, "actor");
        int updated = jdbc.update(
                "update cs_data_import_batch set status = ?, warning_count = ?, "
                        + "blocking_error_count = ?, staging_expires_at = ?, confirmed_at = ?, "
                        + "confirmed_by = ?, updated_by = ?, version = version + 1 "
                        + "where id = ? and tenant_id = ? and workspace_id = ? and version = ?",
                batch.getStatus().name(),
                batch.getWarningCount(),
                batch.getBlockingErrorCount(),
                toTimestamp(batch.getStagingExpiresAt()),
                toTimestamp(batch.getConfirmedAt()),
                batch.getConfirmedBy(),
                auditedActor,
                batch.getId(),
                batch.getScope().getTenantId(),
                batch.getScope().getWorkspaceId(),
                expectedVersion);
        return updated == 1;
    }
}
