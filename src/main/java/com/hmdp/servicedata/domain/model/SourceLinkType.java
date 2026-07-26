package com.hmdp.servicedata.domain.model;

/**
 * Relation kinds between imported facts. Conversation-to-order/case relations always
 * live here as one-to-many links; conversations never embed a single order or case id.
 */
public enum SourceLinkType {
    CONVERSATION_ORDER,
    CONVERSATION_CASE,
    CONSUMER_ORDER,
    CONSUMER_CASE
}
