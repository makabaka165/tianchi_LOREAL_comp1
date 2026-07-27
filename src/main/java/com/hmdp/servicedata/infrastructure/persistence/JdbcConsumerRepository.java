package com.hmdp.servicedata.infrastructure.persistence;

import com.hmdp.servicedata.domain.model.Consumer;
import com.hmdp.servicedata.domain.model.ScopeRef;
import com.hmdp.servicedata.domain.repository.ConsumerRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcConsumerRepository implements ConsumerRepository {
    private final JdbcTemplate jdbc;

    public JdbcConsumerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertIfAbsent(Consumer consumer, String actor) {
        String auditedActor = ScopeRef.requireText(actor, "actor");
        jdbc.update("insert into cs_data_consumer (id, tenant_id, workspace_id, "
                        + "display_name, merge_policy, created_by, updated_by, version) "
                        + "values (?,?,?,?,?,?,?,?) on duplicate key update "
                        + "id = cs_data_consumer.id",
                consumer.getId(), consumer.getScope().getTenantId(),
                consumer.getScope().getWorkspaceId(), consumer.getDisplayName(),
                consumer.getMergePolicy(), auditedActor, auditedActor, consumer.getVersion());
    }

    @Override
    public Optional<Consumer> findById(ScopeRef scope, String consumerId) {
        List<Consumer> rows = jdbc.query(
                "select id, tenant_id, workspace_id, display_name, merge_policy, version "
                        + "from cs_data_consumer where id = ? and tenant_id = ? and workspace_id = ?",
                (rs, rowNum) -> new Consumer(rs.getString("id"),
                        new ScopeRef(rs.getString("tenant_id"), rs.getString("workspace_id")),
                        rs.getString("display_name"), rs.getString("merge_policy"),
                        rs.getInt("version")),
                consumerId, scope.getTenantId(), scope.getWorkspaceId());
        return rows.stream().findFirst();
    }
}
