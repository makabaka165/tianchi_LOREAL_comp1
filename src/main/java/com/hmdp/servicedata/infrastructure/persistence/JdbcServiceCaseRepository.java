package com.hmdp.servicedata.infrastructure.persistence;

import com.hmdp.servicedata.domain.model.ScopeRef;
import com.hmdp.servicedata.domain.model.ServiceCase;
import com.hmdp.servicedata.domain.repository.ServiceCaseRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcServiceCaseRepository implements ServiceCaseRepository {
    private final JdbcTemplate jdbc;

    public JdbcServiceCaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(ServiceCase serviceCase) {
        jdbc.update("insert into cs_data_service_case (id, tenant_id, workspace_id, case_no, "
                        + "case_seq, source_system, source_key, case_type, case_status, priority, "
                        + "order_no, opened_at, closed_at, description, resolution, "
                        + "detail_schema_version, detail_json, content_hash, import_batch_id) "
                        + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                serviceCase.getId(), serviceCase.getScope().getTenantId(),
                serviceCase.getScope().getWorkspaceId(), serviceCase.getCaseNo(),
                serviceCase.getCaseSeq(), serviceCase.getSourceSystem(), serviceCase.getSourceKey(),
                serviceCase.getCaseType(), serviceCase.getCaseStatus(), serviceCase.getPriority(),
                serviceCase.getOrderNo(),
                JdbcServiceDataSupport.timestamp(serviceCase.getOpenedAt()),
                JdbcServiceDataSupport.timestamp(serviceCase.getClosedAt()),
                serviceCase.getDescription(), serviceCase.getResolution(),
                serviceCase.getDetailSchemaVersion(), serviceCase.getDetailJson(),
                serviceCase.getContentHash(), serviceCase.getImportBatchId());
    }

    @Override
    public boolean existsByContent(ScopeRef scope, String caseNo, String contentHash) {
        return count("select count(*) from cs_data_service_case where tenant_id = ? "
                        + "and workspace_id = ? and case_no = ? and content_hash = ?",
                scope, caseNo, contentHash) > 0;
    }

    @Override
    public boolean existsByCaseNo(ScopeRef scope, String caseNo) {
        return count("select count(*) from cs_data_service_case where tenant_id = ? "
                        + "and workspace_id = ? and case_no = ?",
                scope, caseNo) > 0;
    }

    @Override
    public int nextCaseSeq(ScopeRef scope, String caseNo) {
        List<Integer> rows = jdbc.query(
                "select case_seq from cs_data_service_case where tenant_id = ? "
                        + "and workspace_id = ? and case_no = ? "
                        + "order by case_seq desc limit 1 for update",
                (rs, rowNum) -> rs.getInt("case_seq"),
                scope.getTenantId(), scope.getWorkspaceId(), caseNo);
        return rows.isEmpty() ? 1 : rows.get(0) + 1;
    }

    @Override
    public long countByScope(ScopeRef scope) {
        Long count = jdbc.queryForObject(
                "select count(*) from cs_data_service_case where tenant_id = ? and workspace_id = ?",
                Long.class, scope.getTenantId(), scope.getWorkspaceId());
        return count == null ? 0L : count;
    }

    private long count(String sql, ScopeRef scope, Object... tail) {
        Object[] arguments = new Object[2 + tail.length];
        arguments[0] = scope.getTenantId();
        arguments[1] = scope.getWorkspaceId();
        System.arraycopy(tail, 0, arguments, 2, tail.length);
        Long count = jdbc.queryForObject(sql, Long.class, arguments);
        return count == null ? 0L : count;
    }
}
