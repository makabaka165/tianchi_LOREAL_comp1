package com.hmdp.servicedata.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One imported message. Ordering is by {@code sourceSequence} (timestamps are only a
 * secondary sort); the unique source key is the source message id when present or a
 * stable composite hash otherwise — never a bare timestamp. A media path without a
 * retrievable file is recorded as MISSING_MEDIA and must never be presented as
 * processed visual evidence.
 */
public final class Message {
    public static final String MEDIA_STATUS_MISSING = "MISSING_MEDIA";

    private final String id;
    private final ScopeRef scope;
    private final String conversationId;
    private final String sourceMessageKey;
    private final String senderRole;
    private final String senderAlias;
    private final String content;
    private final String contentType;
    private final String mediaPath;
    private final String mediaStatus;
    private final Instant sentAt;
    private final int sourceSequence;
    private final String importBatchId;

    public Message(String id, ScopeRef scope, String conversationId, String sourceMessageKey,
                   String senderRole, String senderAlias, String content, String contentType,
                   String mediaPath, String mediaStatus, Instant sentAt, int sourceSequence,
                   String importBatchId) {
        this.id = ScopeRef.requireText(id, "id");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.conversationId = ScopeRef.requireText(conversationId, "conversationId");
        this.sourceMessageKey = ScopeRef.requireText(sourceMessageKey, "sourceMessageKey");
        this.senderRole = ScopeRef.requireText(senderRole, "senderRole");
        this.senderAlias = senderAlias;
        boolean hasText = content != null && !content.trim().isEmpty();
        boolean hasMedia = mediaPath != null && !mediaPath.trim().isEmpty();
        if (!hasText && !hasMedia) {
            throw new IllegalArgumentException("message requires content or a media reference");
        }
        this.content = content;
        this.contentType = contentType == null || contentType.trim().isEmpty() ? "TEXT" : contentType.trim();
        this.mediaPath = mediaPath;
        this.mediaStatus = mediaStatus;
        this.sentAt = sentAt;
        if (sourceSequence < 0) {
            throw new IllegalArgumentException("sourceSequence must not be negative");
        }
        this.sourceSequence = sourceSequence;
        this.importBatchId = importBatchId;
    }

    public boolean isMediaMissing() {
        return MEDIA_STATUS_MISSING.equals(mediaStatus);
    }

    public String getId() {
        return id;
    }

    public ScopeRef getScope() {
        return scope;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getSourceMessageKey() {
        return sourceMessageKey;
    }

    public String getSenderRole() {
        return senderRole;
    }

    public String getSenderAlias() {
        return senderAlias;
    }

    public String getContent() {
        return content;
    }

    public String getContentType() {
        return contentType;
    }

    public String getMediaPath() {
        return mediaPath;
    }

    public String getMediaStatus() {
        return mediaStatus;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public int getSourceSequence() {
        return sourceSequence;
    }

    public String getImportBatchId() {
        return importBatchId;
    }
}
