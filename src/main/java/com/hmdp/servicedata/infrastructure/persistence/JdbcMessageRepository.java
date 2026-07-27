package com.hmdp.servicedata.infrastructure.persistence;

import com.hmdp.servicedata.domain.model.Message;
import com.hmdp.servicedata.domain.model.ScopeRef;
import com.hmdp.servicedata.domain.repository.MessageRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcMessageRepository implements MessageRepository {
    private final JdbcTemplate jdbc;

    public JdbcMessageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Message message) {
        jdbc.update("insert into cs_data_message (id, tenant_id, workspace_id, conversation_id, "
                        + "source_message_key, sender_role, sender_alias, content, content_type, "
                        + "media_path, media_status, sent_at, source_sequence, import_batch_id) "
                        + "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                message.getId(), message.getScope().getTenantId(),
                message.getScope().getWorkspaceId(), message.getConversationId(),
                message.getSourceMessageKey(), message.getSenderRole(), message.getSenderAlias(),
                message.getContent(), message.getContentType(), message.getMediaPath(),
                message.getMediaStatus(), JdbcServiceDataSupport.timestamp(message.getSentAt()),
                message.getSourceSequence(), message.getImportBatchId());
    }

    @Override
    public Optional<Message> findBySourceKey(ScopeRef scope, String conversationId,
                                              String sourceMessageKey) {
        List<Message> rows = jdbc.query(
                "select id, tenant_id, workspace_id, conversation_id, source_message_key, "
                        + "sender_role, sender_alias, content, content_type, media_path, "
                        + "media_status, sent_at, source_sequence, import_batch_id "
                        + "from cs_data_message where tenant_id = ? and workspace_id = ? "
                        + "and conversation_id = ? and source_message_key = ?",
                (rs, rowNum) -> new Message(rs.getString("id"),
                        new ScopeRef(rs.getString("tenant_id"), rs.getString("workspace_id")),
                        rs.getString("conversation_id"), rs.getString("source_message_key"),
                        rs.getString("sender_role"), rs.getString("sender_alias"),
                        rs.getString("content"), rs.getString("content_type"),
                        rs.getString("media_path"), rs.getString("media_status"),
                        JdbcServiceDataSupport.instant(rs.getTimestamp("sent_at")),
                        rs.getInt("source_sequence"), rs.getString("import_batch_id")),
                scope.getTenantId(), scope.getWorkspaceId(), conversationId, sourceMessageKey);
        return rows.stream().findFirst();
    }

    @Override
    public long countByConversation(String conversationId) {
        Long count = jdbc.queryForObject(
                "select count(*) from cs_data_message where conversation_id = ?",
                Long.class, conversationId);
        return count == null ? 0L : count;
    }

    @Override
    public long countByScope(ScopeRef scope) {
        Long count = jdbc.queryForObject(
                "select count(*) from cs_data_message where tenant_id = ? and workspace_id = ?",
                Long.class, scope.getTenantId(), scope.getWorkspaceId());
        return count == null ? 0L : count;
    }
}
