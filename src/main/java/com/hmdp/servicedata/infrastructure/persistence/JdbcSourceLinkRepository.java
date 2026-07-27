package com.hmdp.servicedata.infrastructure.persistence;

import com.hmdp.servicedata.domain.model.ScopeRef;
import com.hmdp.servicedata.domain.model.SourceLink;
import com.hmdp.servicedata.domain.model.SourceLinkType;
import com.hmdp.servicedata.domain.repository.SourceLinkRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSourceLinkRepository implements SourceLinkRepository {
    private final JdbcTemplate jdbc;

    public JdbcSourceLinkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(SourceLink link) {
        jdbc.update("insert into cs_data_source_link (id, tenant_id, workspace_id, link_type, "
                        + "from_id, to_ref, confidence, provenance_json, import_batch_id) "
                        + "values (?,?,?,?,?,?,?,?,?)",
                link.getId(), link.getScope().getTenantId(), link.getScope().getWorkspaceId(),
                link.getLinkType().name(), link.getFromId(), link.getToRef(),
                link.getConfidence(), link.getProvenanceJson(), link.getImportBatchId());
    }

    @Override
    public boolean exists(ScopeRef scope, SourceLinkType type, String fromId, String toRef) {
        Long count = jdbc.queryForObject(
                "select count(*) from cs_data_source_link where tenant_id = ? and workspace_id = ? "
                        + "and link_type = ? and from_id = ? and to_ref = ?",
                Long.class, scope.getTenantId(), scope.getWorkspaceId(), type.name(), fromId, toRef);
        return count != null && count > 0;
    }

    @Override
    public long countByScope(ScopeRef scope) {
        Long count = jdbc.queryForObject(
                "select count(*) from cs_data_source_link where tenant_id = ? and workspace_id = ?",
                Long.class, scope.getTenantId(), scope.getWorkspaceId());
        return count == null ? 0L : count;
    }
}
