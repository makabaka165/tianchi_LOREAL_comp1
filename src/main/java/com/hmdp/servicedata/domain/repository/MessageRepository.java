package com.hmdp.servicedata.domain.repository;

import com.hmdp.servicedata.domain.model.Message;
import com.hmdp.servicedata.domain.model.ScopeRef;

import java.util.Optional;

public interface MessageRepository {
    void insert(Message message);

    Optional<Message> findBySourceKey(ScopeRef scope, String conversationId,
                                      String sourceMessageKey);

    long countByConversation(String conversationId);

    long countByScope(ScopeRef scope);
}
