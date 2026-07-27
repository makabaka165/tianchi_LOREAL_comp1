package com.hmdp.servicedata.infrastructure.persistence;

import com.hmdp.servicedata.domain.model.ConsumerAlias;
import com.hmdp.servicedata.domain.model.ScopeRef;
import com.hmdp.servicedata.domain.repository.ConsumerAliasRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcConsumerAliasRepository implements ConsumerAliasRepository {
    private final JdbcTemplate jdbc;

    public JdbcConsumerAliasRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(ConsumerAlias alias) {
        int inserted = jdbc.update(
                "insert into cs_data_consumer_alias (id, tenant_id, workspace_id, "
                        + "consumer_id, source_system, source_scope, display_alias, "
                        + "normalized_alias_hash, merge_confidence, provenance_json, "
                        + "import_batch_id) values (?,?,?,?,?,?,?,?,?,?,?) "
                        + "on duplicate key update id = cs_data_consumer_alias.id",
                alias.getId(), alias.getScope().getTenantId(), alias.getScope().getWorkspaceId(),
                alias.getConsumerId(), alias.getSourceSystem(), alias.getSourceScope(),
                alias.getDisplayAlias(), alias.getNormalizedAliasHash(),
                alias.getMergeConfidence(), alias.getProvenanceJson(), alias.getImportBatchId());
        return inserted == 1;
    }

    @Override
    public Optional<ConsumerAlias> findByIdentity(ScopeRef scope, String sourceSystem,
                                                   String sourceScope,
                                                   String normalizedAliasHash) {
        List<ConsumerAlias> rows = jdbc.query(
                "select id, tenant_id, workspace_id, consumer_id, source_system, source_scope, "
                        + "display_alias, merge_confidence, provenance_json, import_batch_id "
                        + "from cs_data_consumer_alias where tenant_id = ? and workspace_id = ? "
                        + "and source_system = ? and source_scope = ? "
                        + "and normalized_alias_hash = ?",
                (rs, rowNum) -> new ConsumerAlias(rs.getString("id"),
                        new ScopeRef(rs.getString("tenant_id"), rs.getString("workspace_id")),
                        rs.getString("consumer_id"), rs.getString("source_system"),
                        rs.getString("source_scope"), rs.getString("display_alias"),
                        rs.getString("merge_confidence"), rs.getString("provenance_json"),
                        rs.getString("import_batch_id")),
                scope.getTenantId(), scope.getWorkspaceId(), sourceSystem, sourceScope,
                normalizedAliasHash);
        return rows.stream().findFirst();
    }

    @Override
    public long countByScope(ScopeRef scope) {
        Long count = jdbc.queryForObject(
                "select count(*) from cs_data_consumer_alias where tenant_id = ? and workspace_id = ?",
                Long.class, scope.getTenantId(), scope.getWorkspaceId());
        return count == null ? 0L : count;
    }
}
