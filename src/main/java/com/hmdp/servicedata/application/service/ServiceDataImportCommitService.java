package com.hmdp.servicedata.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hmdp.servicedata.application.contract.ServiceDataImportCommitSummary;
import com.hmdp.servicedata.application.contract.ServiceDataImportCounts;
import com.hmdp.servicedata.application.imports.ImportRows;
import com.hmdp.servicedata.application.imports.StagedImportRows;
import com.hmdp.servicedata.application.port.in.CommitStagedServiceDataImportUseCase;
import com.hmdp.servicedata.application.port.out.ImportStagingPort;
import com.hmdp.servicedata.domain.model.Consumer;
import com.hmdp.servicedata.domain.model.ConsumerAlias;
import com.hmdp.servicedata.domain.model.Conversation;
import com.hmdp.servicedata.domain.model.Message;
import com.hmdp.servicedata.domain.model.OrderSnapshot;
import com.hmdp.servicedata.domain.model.RecordType;
import com.hmdp.servicedata.domain.model.ScopeRef;
import com.hmdp.servicedata.domain.model.ServiceCase;
import com.hmdp.servicedata.domain.model.ServiceDataSource;
import com.hmdp.servicedata.domain.model.SourceLink;
import com.hmdp.servicedata.domain.model.SourceLinkType;
import com.hmdp.servicedata.domain.repository.ConsumerAliasRepository;
import com.hmdp.servicedata.domain.repository.ConsumerRepository;
import com.hmdp.servicedata.domain.repository.ConversationRepository;
import com.hmdp.servicedata.domain.repository.MessageRepository;
import com.hmdp.servicedata.domain.repository.OrderSnapshotRepository;
import com.hmdp.servicedata.domain.repository.ServiceCaseRepository;
import com.hmdp.servicedata.domain.repository.SourceLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Commits one typed staging batch to scoped source-fact tables in dependency order. */
@Service
public class ServiceDataImportCommitService implements CommitStagedServiceDataImportUseCase {
    private static final String SOURCE_SYSTEM = ServiceDataSource.COMPETITION_WORKBOOK;

    private final ImportStagingPort staging;
    private final ConsumerRepository consumers;
    private final ConsumerAliasRepository aliases;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final OrderSnapshotRepository orders;
    private final ServiceCaseRepository serviceCases;
    private final SourceLinkRepository links;
    private final ConsumerIdentityResolutionPolicy identityPolicy;
    private final ObjectMapper mapper;

    public ServiceDataImportCommitService(ImportStagingPort staging,
                                          ConsumerRepository consumers,
                                          ConsumerAliasRepository aliases,
                                          ConversationRepository conversations,
                                          MessageRepository messages,
                                          OrderSnapshotRepository orders,
                                          ServiceCaseRepository serviceCases,
                                          SourceLinkRepository links,
                                          ConsumerIdentityResolutionPolicy identityPolicy,
                                          ObjectMapper mapper) {
        this.staging = staging;
        this.consumers = consumers;
        this.aliases = aliases;
        this.conversations = conversations;
        this.messages = messages;
        this.orders = orders;
        this.serviceCases = serviceCases;
        this.links = links;
        this.identityPolicy = identityPolicy;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ServiceDataImportCommitSummary commit(ScopeRef scope, String batchId, String actor) {
        StagedImportRows rows = staging.loadForCommit(scope, batchId);
        requireCompleteStaging(staging.findCounts(scope, batchId), rows.counts());

        MutableCounts created = new MutableCounts();
        MutableCounts updated = new MutableCounts();
        MutableCounts skipped = new MutableCounts();
        Map<String, String> consumersByAlias = new HashMap<>();
        Map<String, String> conversationIds = new HashMap<>();

        for (ImportRows.ConsumerAliasRow row : rows.getAliases()) {
            String consumerId = resolveAlias(scope, batchId, actor, row, created, skipped);
            consumersByAlias.put(aliasKey(row.sourceScope, row.displayAlias), consumerId);
        }
        for (ImportRows.ConversationRow row : rows.getConversations()) {
            String consumerId = resolveConversationConsumer(scope, batchId, actor, row,
                    consumersByAlias);
            String conversationId = commitConversation(scope, batchId, actor, row, consumerId,
                    created, updated, skipped);
            conversationIds.put(row.sourceConversationId, conversationId);
        }
        for (ImportRows.MessageRow row : rows.getMessages()) {
            commitMessage(scope, batchId, row, conversationIds, created, skipped);
        }
        for (ImportRows.OrderRow row : rows.getOrders()) {
            commitOrder(scope, batchId, row, created, updated, skipped);
        }
        for (ImportRows.ServiceCaseRow row : rows.getServiceCases()) {
            commitServiceCase(scope, batchId, row, created, updated, skipped);
        }
        for (ImportRows.SourceLinkRow row : rows.getLinks()) {
            commitLink(scope, batchId, row, conversationIds, created, skipped);
        }
        return new ServiceDataImportCommitSummary(created.toValue(), updated.toValue(),
                skipped.toValue());
    }

    private String resolveAlias(ScopeRef scope, String batchId, String actor,
                                ImportRows.ConsumerAliasRow row, MutableCounts created,
                                MutableCounts skipped) {
        String sourceScope = ScopeRef.requireText(row.sourceScope, "sourceScope");
        String aliasHash = identityPolicy.normalizedAliasHash(row.displayAlias);
        Optional<ConsumerAlias> existing = aliases.findByIdentity(scope, SOURCE_SYSTEM,
                sourceScope, aliasHash);
        if (existing.isPresent()) {
            skipped.add(RecordType.CONSUMER_ALIAS, false);
            return existing.get().getConsumerId();
        }

        Consumer consumer = identityPolicy.newConsumer(scope, SOURCE_SYSTEM, sourceScope,
                aliasHash, row.displayAlias);
        consumers.insertIfAbsent(consumer, actor);
        ConsumerAlias alias = identityPolicy.newAlias(scope, SOURCE_SYSTEM, sourceScope,
                row.displayAlias, consumer.getId(), aliasProvenance(batchId, sourceScope), batchId);
        boolean inserted = aliases.insertIfAbsent(alias);
        ConsumerAlias resolved = inserted ? alias : aliases.findByIdentity(scope, SOURCE_SYSTEM,
                        sourceScope, aliasHash)
                .orElseThrow(() -> new ServiceDataImportCommitException(
                        "consumer alias could not be resolved"));
        (inserted ? created : skipped).add(RecordType.CONSUMER_ALIAS, false);
        return resolved.getConsumerId();
    }

    private String resolveConversationConsumer(ScopeRef scope, String batchId, String actor,
                                               ImportRows.ConversationRow row,
                                               Map<String, String> consumersByAlias) {
        if (row.consumerAlias == null || row.consumerAlias.trim().isEmpty()) {
            Consumer anonymous = identityPolicy.newConversationScopedConsumer(scope, SOURCE_SYSTEM,
                    row.sourceConversationId);
            consumers.insertIfAbsent(anonymous, actor);
            return anonymous.getId();
        }
        String aliasScope = identityPolicy.aliasScopeForConversation(row.sourceScope);
        String key = aliasKey(aliasScope, row.consumerAlias);
        String consumerId = consumersByAlias.get(key);
        if (consumerId != null) {
            return consumerId;
        }
        String aliasHash = identityPolicy.normalizedAliasHash(row.consumerAlias);
        return aliases.findByIdentity(scope, SOURCE_SYSTEM, aliasScope, aliasHash)
                .map(ConsumerAlias::getConsumerId)
                .orElseThrow(() -> new ServiceDataImportCommitException(
                        "conversation consumer alias is missing from staging"));
    }

    private String commitConversation(ScopeRef scope, String batchId, String actor,
                                      ImportRows.ConversationRow row, String consumerId,
                                      MutableCounts created, MutableCounts updated,
                                      MutableCounts skipped) {
        Optional<Conversation> existing = conversations.findBySourceKey(scope, SOURCE_SYSTEM,
                row.sourceConversationId);
        String id = existing.map(Conversation::getId).orElseGet(() -> identityPolicy.stableId(
                "conversation", scope, SOURCE_SYSTEM, row.sourceConversationId));
        String contentHash = conversationHash(row, consumerId);
        Conversation candidate = new Conversation(id, scope, SOURCE_SYSTEM,
                row.sourceConversationId, consumerId, "CHAT", null, row.firstMessageAt,
                row.lastMessageAt, row.messageCount, row.firstMessageAt, row.lastMessageAt,
                contentHash, batchId, existing.map(Conversation::getVersion).orElse(0));
        if (existing.isEmpty()) {
            conversations.insert(candidate, actor);
            created.add(RecordType.CONVERSATION, false);
        } else if (contentHash.equals(existing.get().getContentHash())) {
            skipped.add(RecordType.CONVERSATION, false);
        } else {
            if (!conversations.updateWithVersion(candidate, existing.get().getVersion(), actor)) {
                throw new ServiceDataImportCommitException("conversation version conflict");
            }
            updated.add(RecordType.CONVERSATION, false);
        }
        return id;
    }

    private void commitMessage(ScopeRef scope, String batchId, ImportRows.MessageRow row,
                               Map<String, String> conversationIds, MutableCounts created,
                               MutableCounts skipped) {
        String conversationId = requireConversationId(scope, row.sourceConversationId,
                conversationIds);
        Message candidate = new Message(identityPolicy.stableId("message", scope,
                conversationId, row.sourceMessageId), scope, conversationId,
                row.sourceMessageId, row.senderRole, row.senderAlias, row.content,
                row.contentType, row.mediaPath, row.mediaStatus, row.sentAt,
                row.sourceSequence, batchId);
        Optional<Message> existing = messages.findBySourceKey(scope, conversationId,
                row.sourceMessageId);
        boolean missingMedia = candidate.isMediaMissing();
        if (existing.isEmpty()) {
            messages.insert(candidate);
            created.add(RecordType.MESSAGE, missingMedia);
        } else if (messageHash(existing.get()).equals(messageHash(candidate))) {
            skipped.add(RecordType.MESSAGE, missingMedia);
        } else {
            throw new ServiceDataImportCommitException(
                    "immutable message source key has different content");
        }
    }

    private void commitOrder(ScopeRef scope, String batchId, ImportRows.OrderRow row,
                             MutableCounts created, MutableCounts updated,
                             MutableCounts skipped) {
        String detailJson = orderDetailJson(row);
        String contentHash = orderHash(row, detailJson);
        if (orders.existsByContent(scope, row.orderNo, contentHash)) {
            skipped.add(RecordType.ORDER_SNAPSHOT, false);
            return;
        }
        int sequence = orders.nextSnapshotSeq(scope, row.orderNo);
        OrderSnapshot snapshot = new OrderSnapshot(identityPolicy.stableId("order", scope,
                row.orderNo, contentHash), scope, row.orderNo, sequence, SOURCE_SYSTEM,
                row.orderNo, row.orderStatus, row.productName, row.sku, row.quantity,
                row.paidAmount, monetaryCurrency(row), row.orderedAt, row.paidAt, row.shippedAt,
                null, row.logisticsNo, row.logisticsCompany, 1, detailJson, contentHash, batchId);
        orders.insert(snapshot);
        (sequence == 1 ? created : updated).add(RecordType.ORDER_SNAPSHOT, false);
    }

    private void commitServiceCase(ScopeRef scope, String batchId,
                                   ImportRows.ServiceCaseRow row, MutableCounts created,
                                   MutableCounts updated, MutableCounts skipped) {
        String detailJson = caseDetailJson(row);
        String contentHash = caseHash(row, detailJson);
        if (serviceCases.existsByContent(scope, row.caseNo, contentHash)) {
            skipped.add(RecordType.SERVICE_CASE, false);
            return;
        }
        int sequence = serviceCases.nextCaseSeq(scope, row.caseNo);
        ServiceCase serviceCase = new ServiceCase(identityPolicy.stableId("case", scope,
                row.caseNo, contentHash), scope, row.caseNo, sequence, SOURCE_SYSTEM,
                row.caseNo, row.caseType, row.caseStatus, null, row.orderNo, row.openedAt,
                row.closedAt, row.description, null, 1, detailJson, contentHash, batchId);
        serviceCases.insert(serviceCase);
        (sequence == 1 ? created : updated).add(RecordType.SERVICE_CASE, false);
    }

    private void commitLink(ScopeRef scope, String batchId, ImportRows.SourceLinkRow row,
                            Map<String, String> conversationIds, MutableCounts created,
                            MutableCounts skipped) {
        if (row.quarantined) {
            throw new ServiceDataImportCommitException("quarantined source link cannot commit");
        }
        SourceLinkType type;
        try {
            type = SourceLinkType.valueOf(row.linkType);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ServiceDataImportCommitException("unsupported source link type");
        }
        String fromId;
        switch (type) {
            case CONVERSATION_ORDER:
                fromId = requireConversationId(scope, row.fromConversationId, conversationIds);
                if (!orders.existsByOrderNo(scope, row.toRef)) {
                    throw new ServiceDataImportCommitException("source link target is missing");
                }
                break;
            case CONVERSATION_CASE:
                fromId = requireConversationId(scope, row.fromConversationId, conversationIds);
                if (!serviceCases.existsByCaseNo(scope, row.toRef)) {
                    throw new ServiceDataImportCommitException("source link target is missing");
                }
                break;
            default:
                throw new ServiceDataImportCommitException(
                        "consumer source links require an explicit consumer source key");
        }
        if (links.exists(scope, type, fromId, row.toRef)) {
            skipped.add(RecordType.SOURCE_LINK, false);
            return;
        }
        SourceLink link = new SourceLink(identityPolicy.stableId("link", scope, type.name(),
                fromId, row.toRef), scope, type, fromId, row.toRef, "SOURCE",
                linkProvenance(batchId, row.fromConversationId), batchId);
        links.insert(link);
        created.add(RecordType.SOURCE_LINK, false);
    }

    private String requireConversationId(ScopeRef scope, String sourceConversationId,
                                         Map<String, String> conversationIds) {
        String id = conversationIds.get(sourceConversationId);
        if (id != null) {
            return id;
        }
        return conversations.findBySourceKey(scope, SOURCE_SYSTEM, sourceConversationId)
                .map(Conversation::getId)
                .orElseThrow(() -> new ServiceDataImportCommitException(
                        "source conversation is missing"));
    }

    private void requireCompleteStaging(ServiceDataImportCounts expected,
                                        ServiceDataImportCounts actual) {
        if (expected.getConsumerAliases() != actual.getConsumerAliases()
                || expected.getConversations() != actual.getConversations()
                || expected.getMessages() != actual.getMessages()
                || expected.getOrderSnapshots() != actual.getOrderSnapshots()
                || expected.getServiceCases() != actual.getServiceCases()
                || expected.getSourceLinks() != actual.getSourceLinks()
                || expected.getMissingMedia() != actual.getMissingMedia()) {
            throw new ServiceDataImportCommitException("staging counts do not match preview");
        }
    }

    private String aliasKey(String sourceScope, String displayAlias) {
        return sourceScope + '|' + identityPolicy.normalizedAliasHash(displayAlias);
    }

    private String conversationHash(ImportRows.ConversationRow row, String consumerId) {
        ObjectNode node = mapper.createObjectNode();
        node.put("sourceSystem", SOURCE_SYSTEM);
        node.put("sourceConversationId", row.sourceConversationId);
        node.put("consumerId", consumerId);
        node.put("channel", "CHAT");
        node.put("messageCount", row.messageCount);
        put(node, "firstMessageAt", row.firstMessageAt);
        put(node, "lastMessageAt", row.lastMessageAt);
        return hash(node);
    }

    private String messageHash(Message message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("conversationId", message.getConversationId());
        node.put("sourceMessageKey", message.getSourceMessageKey());
        put(node, "senderRole", message.getSenderRole());
        put(node, "senderAlias", message.getSenderAlias());
        put(node, "content", message.getContent());
        put(node, "contentType", message.getContentType());
        put(node, "mediaPath", message.getMediaPath());
        put(node, "mediaStatus", message.getMediaStatus());
        put(node, "sentAt", message.getSentAt());
        node.put("sourceSequence", message.getSourceSequence());
        return hash(node);
    }

    private String orderHash(ImportRows.OrderRow row, String detailJson) {
        ObjectNode node = mapper.createObjectNode();
        node.put("sourceSystem", SOURCE_SYSTEM);
        node.put("orderNo", row.orderNo);
        put(node, "orderStatus", row.orderStatus);
        put(node, "productName", row.productName);
        put(node, "sku", row.sku);
        put(node, "quantity", row.quantity);
        put(node, "amount", row.paidAmount);
        put(node, "currency", monetaryCurrency(row));
        put(node, "orderedAt", row.orderedAt);
        put(node, "paidAt", row.paidAt);
        put(node, "shippedAt", row.shippedAt);
        put(node, "logisticsNo", row.logisticsNo);
        put(node, "logisticsCompany", row.logisticsCompany);
        node.set("detail", readObject(detailJson));
        return hash(node);
    }

    private String caseHash(ImportRows.ServiceCaseRow row, String detailJson) {
        ObjectNode node = mapper.createObjectNode();
        node.put("sourceSystem", SOURCE_SYSTEM);
        node.put("caseNo", row.caseNo);
        put(node, "caseType", row.caseType);
        put(node, "caseStatus", row.caseStatus);
        put(node, "orderNo", row.orderNo);
        put(node, "openedAt", row.openedAt);
        put(node, "closedAt", row.closedAt);
        put(node, "description", row.description);
        node.set("detail", readObject(detailJson));
        return hash(node);
    }

    private String orderDetailJson(ImportRows.OrderRow row) {
        ObjectNode detail = mapper.createObjectNode();
        put(detail, "unitPrice", row.unitPrice);
        detail.set("source", stringObject(row.detail));
        return json(detail);
    }

    private String caseDetailJson(ImportRows.ServiceCaseRow row) {
        ObjectNode detail = mapper.createObjectNode();
        put(detail, "reason", row.reason);
        detail.set("source", stringObject(row.detail));
        return json(detail);
    }

    private ObjectNode stringObject(Map<String, String> values) {
        ObjectNode node = mapper.createObjectNode();
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            put(node, entry.getKey(), entry.getValue());
        }
        return node;
    }

    private String aliasProvenance(String batchId, String sourceScope) {
        ObjectNode node = mapper.createObjectNode();
        node.put("batchId", batchId);
        node.put("sourceSystem", SOURCE_SYSTEM);
        node.put("sourceScope", sourceScope);
        return json(node);
    }

    private String linkProvenance(String batchId, String sourceConversationId) {
        ObjectNode node = mapper.createObjectNode();
        node.put("batchId", batchId);
        node.put("sourceSystem", SOURCE_SYSTEM);
        node.put("sourceConversationId", sourceConversationId);
        return json(node);
    }

    private ObjectNode readObject(String json) {
        try {
            return (ObjectNode) mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ServiceDataImportCommitException("fact detail serialization failed");
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ServiceDataImportCommitException("fact serialization failed");
        }
    }

    private String hash(ObjectNode node) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsString(node).getBytes(StandardCharsets.UTF_8));
            char[] alphabet = "0123456789abcdef".toCharArray();
            char[] encoded = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                int current = digest[i] & 0xff;
                encoded[i * 2] = alphabet[current >>> 4];
                encoded[i * 2 + 1] = alphabet[current & 0x0f];
            }
            return new String(encoded);
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("SHA-256 fact hashing failed", e);
        }
    }

    private String monetaryCurrency(ImportRows.OrderRow row) {
        return row.unitPrice == null && row.paidAmount == null ? null : "CNY";
    }

    private static void put(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static void put(ObjectNode node, String field, Instant value) {
        // MySQL TIMESTAMP(3) persists millisecond precision. Hash the persisted
        // representation so a semantically identical re-import with sub-millisecond
        // source precision does not look like changed immutable content.
        put(node, field, value == null ? null
                : value.truncatedTo(ChronoUnit.MILLIS).toString());
    }

    private static void put(ObjectNode node, String field, Integer value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static void put(ObjectNode node, String field, BigDecimal value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static final class MutableCounts {
        private int consumerAliases;
        private int conversations;
        private int messages;
        private int orderSnapshots;
        private int serviceCases;
        private int sourceLinks;
        private int missingMedia;

        private void add(RecordType type, boolean mediaMissing) {
            switch (type) {
                case CONSUMER_ALIAS:
                    consumerAliases++;
                    break;
                case CONVERSATION:
                    conversations++;
                    break;
                case MESSAGE:
                    messages++;
                    if (mediaMissing) {
                        missingMedia++;
                    }
                    break;
                case ORDER_SNAPSHOT:
                    orderSnapshots++;
                    break;
                case SERVICE_CASE:
                    serviceCases++;
                    break;
                case SOURCE_LINK:
                    sourceLinks++;
                    break;
                default:
                    throw new IllegalArgumentException("unsupported count type");
            }
        }

        private ServiceDataImportCounts toValue() {
            return new ServiceDataImportCounts(consumerAliases, conversations, messages,
                    orderSnapshots, serviceCases, sourceLinks, missingMedia);
        }
    }
}
