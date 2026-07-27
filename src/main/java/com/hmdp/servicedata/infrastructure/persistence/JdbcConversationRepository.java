package com.hmdp.servicedata.infrastructure.persistence;

import com.hmdp.servicedata.domain.model.Conversation;
import com.hmdp.servicedata.domain.model.ScopeRef;
import com.hmdp.servicedata.domain.repository.ConversationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcConversationRepository implements ConversationRepository {
    private final JdbcTemplate jdbc;

    public JdbcConversationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Conversation conversation, String actor) {
        String auditedActor = ScopeRef.requireText(actor, "actor");
        jdbc.update("insert into cs_data_conversation (id, tenant_id, workspace_id, "
                        + "source_system, source_conversation_id, consumer_id, channel, status, "
                        + "started_at, ended_at, message_count, first_message_at, last_message_at, "
                        + "content_hash, import_batch_id, created_by, updated_by, version) "
                        + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                conversation.getId(), conversation.getScope().getTenantId(),
                conversation.getScope().getWorkspaceId(), conversation.getSourceSystem(),
                conversation.getSourceConversationId(), conversation.getConsumerId(),
                conversation.getChannel(), conversation.getStatus(),
                JdbcServiceDataSupport.timestamp(conversation.getStartedAt()),
                JdbcServiceDataSupport.timestamp(conversation.getEndedAt()),
                conversation.getMessageCount(),
                JdbcServiceDataSupport.timestamp(conversation.getFirstMessageAt()),
                JdbcServiceDataSupport.timestamp(conversation.getLastMessageAt()),
                conversation.getContentHash(), conversation.getImportBatchId(), auditedActor,
                auditedActor, conversation.getVersion());
    }

    @Override
    public Optional<Conversation> findBySourceKey(ScopeRef scope, String sourceSystem,
                                                   String sourceConversationId) {
        List<Conversation> rows = jdbc.query(
                "select id, tenant_id, workspace_id, source_system, source_conversation_id, "
                        + "consumer_id, channel, status, started_at, ended_at, message_count, "
                        + "first_message_at, last_message_at, content_hash, import_batch_id, version "
                        + "from cs_data_conversation where tenant_id = ? and workspace_id = ? "
                        + "and source_system = ? and source_conversation_id = ?",
                (rs, rowNum) -> new Conversation(rs.getString("id"),
                        new ScopeRef(rs.getString("tenant_id"), rs.getString("workspace_id")),
                        rs.getString("source_system"), rs.getString("source_conversation_id"),
                        rs.getString("consumer_id"), rs.getString("channel"),
                        rs.getString("status"),
                        JdbcServiceDataSupport.instant(rs.getTimestamp("started_at")),
                        JdbcServiceDataSupport.instant(rs.getTimestamp("ended_at")),
                        rs.getInt("message_count"),
                        JdbcServiceDataSupport.instant(rs.getTimestamp("first_message_at")),
                        JdbcServiceDataSupport.instant(rs.getTimestamp("last_message_at")),
                        rs.getString("content_hash"), rs.getString("import_batch_id"),
                        rs.getInt("version")),
                scope.getTenantId(), scope.getWorkspaceId(), sourceSystem, sourceConversationId);
        return rows.stream().findFirst();
    }

    @Override
    public boolean updateWithVersion(Conversation conversation, int expectedVersion, String actor) {
        String auditedActor = ScopeRef.requireText(actor, "actor");
        int updated = jdbc.update(
                "update cs_data_conversation set consumer_id = ?, channel = ?, status = ?, "
                        + "started_at = ?, ended_at = ?, message_count = ?, first_message_at = ?, "
                        + "last_message_at = ?, content_hash = ?, import_batch_id = ?, "
                        + "updated_by = ?, version = version + 1 where id = ? and tenant_id = ? "
                        + "and workspace_id = ? and version = ?",
                conversation.getConsumerId(), conversation.getChannel(), conversation.getStatus(),
                JdbcServiceDataSupport.timestamp(conversation.getStartedAt()),
                JdbcServiceDataSupport.timestamp(conversation.getEndedAt()),
                conversation.getMessageCount(),
                JdbcServiceDataSupport.timestamp(conversation.getFirstMessageAt()),
                JdbcServiceDataSupport.timestamp(conversation.getLastMessageAt()),
                conversation.getContentHash(), conversation.getImportBatchId(), auditedActor,
                conversation.getId(), conversation.getScope().getTenantId(),
                conversation.getScope().getWorkspaceId(), expectedVersion);
        return updated == 1;
    }

    @Override
    public long countByScope(ScopeRef scope) {
        Long count = jdbc.queryForObject(
                "select count(*) from cs_data_conversation where tenant_id = ? and workspace_id = ?",
                Long.class, scope.getTenantId(), scope.getWorkspaceId());
        return count == null ? 0L : count;
    }
}
