package com.hmdp.servicedata.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Imported service conversation. Identity is the source key
 * (scope + sourceSystem + sourceConversationId); relations to orders and cases live in
 * {@link SourceLink}, never as columns here, because one conversation may touch many.
 */
public final class Conversation {
    private final String id;
    private final ScopeRef scope;
    private final String sourceSystem;
    private final String sourceConversationId;
    private final String consumerId;
    private final String channel;
    private final String status;
    private final Instant startedAt;
    private final Instant endedAt;
    private final int messageCount;
    private final Instant firstMessageAt;
    private final Instant lastMessageAt;
    private final String contentHash;
    private final String importBatchId;

    public Conversation(String id, ScopeRef scope, String sourceSystem,
                        String sourceConversationId, String consumerId, String channel,
                        String status, Instant startedAt, Instant endedAt, int messageCount,
                        Instant firstMessageAt, Instant lastMessageAt, String contentHash,
                        String importBatchId) {
        this.id = ScopeRef.requireText(id, "id");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.sourceSystem = ScopeRef.requireText(sourceSystem, "sourceSystem");
        this.sourceConversationId = ScopeRef.requireText(sourceConversationId, "sourceConversationId");
        this.consumerId = ScopeRef.requireText(consumerId, "consumerId");
        this.channel = channel;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        if (messageCount < 0) {
            throw new IllegalArgumentException("messageCount must not be negative");
        }
        this.messageCount = messageCount;
        this.firstMessageAt = firstMessageAt;
        this.lastMessageAt = lastMessageAt;
        this.contentHash = contentHash;
        this.importBatchId = importBatchId;
    }

    public String getId() {
        return id;
    }

    public ScopeRef getScope() {
        return scope;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public String getSourceConversationId() {
        return sourceConversationId;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public String getChannel() {
        return channel;
    }

    public String getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public Instant getFirstMessageAt() {
        return firstMessageAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getImportBatchId() {
        return importBatchId;
    }
}
