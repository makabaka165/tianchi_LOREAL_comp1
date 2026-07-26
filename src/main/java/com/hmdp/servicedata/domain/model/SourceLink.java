package com.hmdp.servicedata.domain.model;

import java.util.Objects;

/** Immutable relation between a conversation/consumer and an order or service case. */
public final class SourceLink {
    private final String id;
    private final ScopeRef scope;
    private final SourceLinkType linkType;
    private final String fromId;
    private final String toRef;
    private final String confidence;
    private final String importBatchId;

    public SourceLink(String id, ScopeRef scope, SourceLinkType linkType, String fromId,
                      String toRef, String confidence, String importBatchId) {
        this.id = ScopeRef.requireText(id, "id");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.linkType = Objects.requireNonNull(linkType, "linkType");
        this.fromId = ScopeRef.requireText(fromId, "fromId");
        this.toRef = ScopeRef.requireText(toRef, "toRef");
        this.confidence = confidence;
        this.importBatchId = importBatchId;
    }

    public String getId() {
        return id;
    }

    public ScopeRef getScope() {
        return scope;
    }

    public SourceLinkType getLinkType() {
        return linkType;
    }

    public String getFromId() {
        return fromId;
    }

    public String getToRef() {
        return toRef;
    }

    public String getConfidence() {
        return confidence;
    }

    public String getImportBatchId() {
        return importBatchId;
    }
}
