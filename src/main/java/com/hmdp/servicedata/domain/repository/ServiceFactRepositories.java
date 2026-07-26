package com.hmdp.servicedata.domain.repository;

import com.hmdp.servicedata.domain.model.Consumer;
import com.hmdp.servicedata.domain.model.ConsumerAlias;
import com.hmdp.servicedata.domain.model.Conversation;
import com.hmdp.servicedata.domain.model.Message;
import com.hmdp.servicedata.domain.model.OrderSnapshot;
import com.hmdp.servicedata.domain.model.ScopeRef;
import com.hmdp.servicedata.domain.model.ServiceCase;
import com.hmdp.servicedata.domain.model.SourceLink;
import com.hmdp.servicedata.domain.model.SourceLinkType;

import java.util.Optional;

/**
 * Write/lookup ports for imported facts, grouped in one file until DATA-004 gives each
 * adapter its own weight. Every method is scope-aware; none returns mutable ORM state.
 */
public final class ServiceFactRepositories {

    private ServiceFactRepositories() {
    }

    public interface ConsumerRepository {
        void insert(Consumer consumer);

        Optional<Consumer> findById(ScopeRef scope, String consumerId);
    }

    public interface ConsumerAliasRepository {
        void insert(ConsumerAlias alias);

        Optional<ConsumerAlias> findByIdentity(ScopeRef scope, String sourceSystem,
                                               String sourceScope, String normalizedAliasHash);

        long countByScope(ScopeRef scope);
    }

    public interface ConversationRepository {
        void insert(Conversation conversation);

        Optional<Conversation> findBySourceKey(ScopeRef scope, String sourceSystem,
                                               String sourceConversationId);

        long countByScope(ScopeRef scope);
    }

    public interface MessageRepository {
        void insert(Message message);

        boolean existsBySourceKey(String conversationId, String sourceMessageKey);

        long countByConversation(String conversationId);

        long countByScope(ScopeRef scope);
    }

    public interface OrderSnapshotRepository {
        void insert(OrderSnapshot snapshot);

        boolean existsByContent(ScopeRef scope, String orderNo, String contentHash);

        int maxSnapshotSeq(ScopeRef scope, String orderNo);

        long countByScope(ScopeRef scope);
    }

    public interface ServiceCaseRepository {
        void insert(ServiceCase serviceCase);

        boolean existsByContent(ScopeRef scope, String caseNo, String contentHash);

        int maxCaseSeq(ScopeRef scope, String caseNo);

        long countByScope(ScopeRef scope);
    }

    public interface SourceLinkRepository {
        void insert(SourceLink link);

        boolean exists(ScopeRef scope, SourceLinkType type, String fromId, String toRef);

        long countByScope(ScopeRef scope);
    }
}
