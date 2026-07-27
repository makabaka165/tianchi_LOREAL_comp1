package com.hmdp.servicedata.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.servicedata.application.contract.ServiceDataImportCounts;
import com.hmdp.servicedata.application.contract.ServiceDataImportCommitSummary;
import com.hmdp.servicedata.application.contract.ServiceDataImportErrorPage;
import com.hmdp.servicedata.application.contract.ServiceDataImportErrorView;
import com.hmdp.servicedata.application.imports.ImportIssue;
import com.hmdp.servicedata.application.imports.ImportRows;
import com.hmdp.servicedata.application.imports.StagedImportRows;
import com.hmdp.servicedata.application.imports.WorkbookParseResult;
import com.hmdp.servicedata.application.port.out.ImportStagingPort;
import com.hmdp.servicedata.domain.model.RecordType;
import com.hmdp.servicedata.domain.model.ScopeRef;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Persists typed parser output only into DATA-001 staging and masked error tables. */
@Repository
public class JdbcImportStagingRepository implements ImportStagingPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcImportStagingRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void savePreview(ScopeRef scope, String batchId, Instant expiresAt,
                            WorkbookParseResult result, ServiceDataImportCounts counts) {
        Set<String> stagedKeys = new HashSet<>();
        for (ImportRows.ConsumerAliasRow row : result.getAliases()) {
            stage(scope, batchId, RecordType.CONSUMER_ALIAS, "聊天记录", 0,
                    row.sourceScope + "|" + row.displayAlias, row, expiresAt, stagedKeys);
        }
        for (ImportRows.ConversationRow row : result.getConversations()) {
            stage(scope, batchId, RecordType.CONVERSATION, "聊天记录", 0,
                    row.sourceConversationId, row, expiresAt, stagedKeys);
        }
        for (ImportRows.MessageRow row : result.getMessages()) {
            stage(scope, batchId, RecordType.MESSAGE, "聊天记录", row.rowNo,
                    row.sourceConversationId + "|" + row.sourceMessageId,
                    row, expiresAt, stagedKeys);
        }
        for (ImportRows.OrderRow row : result.getOrders()) {
            stage(scope, batchId, RecordType.ORDER_SNAPSHOT, "订单", row.rowNo,
                    row.orderNo, row, expiresAt, stagedKeys);
        }
        for (ImportRows.ServiceCaseRow row : result.getServiceCases()) {
            stage(scope, batchId, RecordType.SERVICE_CASE, safeSheet(row.caseType), row.rowNo,
                    row.caseNo, row, expiresAt, stagedKeys);
        }
        for (ImportRows.SourceLinkRow row : result.getLinks()) {
            stage(scope, batchId, RecordType.SOURCE_LINK, "来源关联", 0,
                    row.linkType + "|" + row.fromConversationId + "|" + row.toRef,
                    row, expiresAt, stagedKeys);
        }
        for (ImportIssue issue : result.getIssues()) {
            saveIssue(scope, batchId, issue);
        }
        int updated = jdbc.update(
                "update cs_data_import_batch set preview_counts_json = ? "
                        + "where id = ? and tenant_id = ? and workspace_id = ?",
                json(counts), batchId, scope.getTenantId(), scope.getWorkspaceId());
        if (updated != 1) {
            throw new IllegalStateException("import batch disappeared while staging preview");
        }
    }

    @Override
    public ServiceDataImportCounts findCounts(ScopeRef scope, String batchId) {
        List<String> rows = jdbc.query(
                "select preview_counts_json from cs_data_import_batch "
                        + "where id = ? and tenant_id = ? and workspace_id = ?",
                (rs, rowNum) -> rs.getString("preview_counts_json"),
                batchId, scope.getTenantId(), scope.getWorkspaceId());
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).trim().isEmpty()) {
            return ServiceDataImportCounts.empty();
        }
        try {
            JsonNode node = mapper.readTree(rows.get(0));
            return new ServiceDataImportCounts(
                    nonNegative(node, "consumerAliases"),
                    nonNegative(node, "conversations"),
                    nonNegative(node, "messages"),
                    nonNegative(node, "orderSnapshots"),
                    nonNegative(node, "serviceCases"),
                    nonNegative(node, "sourceLinks"),
                    nonNegative(node, "missingMedia"));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored import preview counts are invalid", e);
        }
    }

    @Override
    public ServiceDataImportErrorPage findErrors(ScopeRef scope, String batchId,
                                                 int page, int size) {
        Long total = jdbc.queryForObject(
                "select count(*) from cs_data_import_error "
                        + "where tenant_id = ? and workspace_id = ? and batch_id = ?",
                Long.class, scope.getTenantId(), scope.getWorkspaceId(), batchId);
        long offset = ((long) page - 1L) * size;
        List<ServiceDataImportErrorView> items = jdbc.query(
                "select sheet_name, row_no, field_name, error_code, severity, "
                        + "masked_raw_value, message from cs_data_import_error "
                        + "where tenant_id = ? and workspace_id = ? and batch_id = ? "
                        + "order by case severity when 'BLOCKING' then 0 else 1 end, "
                        + "sheet_name, row_no, id limit ? offset ?",
                (rs, rowNum) -> new ServiceDataImportErrorView(
                        rs.getString("sheet_name"), rs.getInt("row_no"),
                        rs.getString("field_name"), rs.getString("error_code"),
                        rs.getString("severity"), rs.getString("masked_raw_value"),
                        rs.getString("message")),
                scope.getTenantId(), scope.getWorkspaceId(), batchId, size, offset);
        return new ServiceDataImportErrorPage(items, total == null ? 0 : total, page, size);
    }

    @Override
    public StagedImportRows loadForCommit(ScopeRef scope, String batchId) {
        StagedImportRows result = new StagedImportRows();
        jdbc.query("select record_type, payload_json from cs_data_import_staging "
                        + "where tenant_id = ? and workspace_id = ? and batch_id = ? "
                        + "order by record_type, sheet_name, row_no, id",
                (rs, rowNum) -> {
                    addStagedRow(result, RecordType.valueOf(rs.getString("record_type")),
                            rs.getString("payload_json"));
                    return rowNum;
                },
                scope.getTenantId(), scope.getWorkspaceId(), batchId);
        return result;
    }

    @Override
    public void completeCommit(ScopeRef scope, String batchId,
                               ServiceDataImportCommitSummary summary) {
        int updated = jdbc.update(
                "update cs_data_import_batch set commit_counts_json = ? where id = ? "
                        + "and tenant_id = ? and workspace_id = ? and status = 'CONFIRMED'",
                json(summary), batchId, scope.getTenantId(), scope.getWorkspaceId());
        if (updated != 1) {
            throw new IllegalStateException("confirmed import batch disappeared before cleanup");
        }
        jdbc.update("delete from cs_data_import_staging where tenant_id = ? "
                        + "and workspace_id = ? and batch_id = ?",
                scope.getTenantId(), scope.getWorkspaceId(), batchId);
    }

    private void addStagedRow(StagedImportRows result, RecordType type, String payload) {
        switch (type) {
            case CONSUMER_ALIAS:
                result.addAlias(read(payload, ImportRows.ConsumerAliasRow.class));
                break;
            case CONVERSATION:
                result.addConversation(read(payload, ImportRows.ConversationRow.class));
                break;
            case MESSAGE:
                result.addMessage(read(payload, ImportRows.MessageRow.class));
                break;
            case ORDER_SNAPSHOT:
                result.addOrder(read(payload, ImportRows.OrderRow.class));
                break;
            case SERVICE_CASE:
                result.addServiceCase(read(payload, ImportRows.ServiceCaseRow.class));
                break;
            case SOURCE_LINK:
                result.addLink(read(payload, ImportRows.SourceLinkRow.class));
                break;
            default:
                throw new IllegalStateException("unsupported staged record type");
        }
    }

    private <T> T read(String payload, Class<T> type) {
        try {
            return mapper.readValue(payload, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored import staging payload is invalid", e);
        }
    }

    private void stage(ScopeRef scope, String batchId, RecordType type, String sheet,
                       int rowNo, String rawSourceKey, Object payload, Instant expiresAt,
                       Set<String> stagedKeys) {
        String sourceKey = sha256(type.name() + "|" + value(rawSourceKey));
        String deduplicationKey = type.name() + "|" + sourceKey;
        if (!stagedKeys.add(deduplicationKey)) {
            return;
        }
        jdbc.update(
                "insert into cs_data_import_staging (id, tenant_id, workspace_id, batch_id, "
                        + "record_type, sheet_name, row_no, source_key, payload_json, expires_at) "
                        + "values (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), scope.getTenantId(), scope.getWorkspaceId(),
                batchId, type.name(), truncate(sheet, 128), Math.max(0, rowNo), sourceKey,
                json(payload), toTimestamp(expiresAt));
    }

    private void saveIssue(ScopeRef scope, String batchId, ImportIssue issue) {
        jdbc.update(
                "insert into cs_data_import_error (id, tenant_id, workspace_id, batch_id, "
                        + "sheet_name, row_no, field_name, error_code, severity, "
                        + "masked_raw_value, message) values (?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), scope.getTenantId(), scope.getWorkspaceId(),
                batchId, truncate(issue.getSheet(), 128), Math.max(0, issue.getRowNo()),
                truncateNullable(issue.getField(), 128), truncate(issue.getErrorCode(), 64),
                issue.getSeverity().name(), truncateNullable(issue.getMaskedValue(), 255),
                truncate(issue.getMessage(), 500));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("import staging serialization failed", e);
        }
    }

    private static int nonNegative(JsonNode node, String field) {
        return Math.max(0, node.path(field).asInt(0));
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static String safeSheet(String sheet) {
        return sheet == null || sheet.trim().isEmpty() ? "服务工单" : sheet.trim();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String truncateNullable(String value, int maxLength) {
        return value == null ? null : truncate(value, maxLength);
    }

    private static String truncate(String value, int maxLength) {
        String safe = value == null ? "" : value;
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            char[] alphabet = "0123456789abcdef".toCharArray();
            char[] encoded = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                int current = digest[i] & 0xff;
                encoded[i * 2] = alphabet[current >>> 4];
                encoded[i * 2 + 1] = alphabet[current & 0x0f];
            }
            return new String(encoded);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
