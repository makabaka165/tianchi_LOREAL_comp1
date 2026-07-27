package com.hmdp.servicedata.domain.repository;

import com.hmdp.servicedata.domain.model.Conversation;
import com.hmdp.servicedata.domain.model.ScopeRef;

import java.util.Optional;

public interface ConversationRepository {
    void insert(Conversation conversation, String actor);

    Optional<Conversation> findBySourceKey(ScopeRef scope, String sourceSystem,
                                           String sourceConversationId);

    boolean updateWithVersion(Conversation conversation, int expectedVersion, String actor);

    long countByScope(ScopeRef scope);
}
