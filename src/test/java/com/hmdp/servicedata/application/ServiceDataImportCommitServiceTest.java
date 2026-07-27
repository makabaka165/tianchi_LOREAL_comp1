package com.hmdp.servicedata.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.servicedata.application.contract.ServiceDataImportCounts;
import com.hmdp.servicedata.application.imports.ImportRows;
import com.hmdp.servicedata.application.imports.StagedImportRows;
import com.hmdp.servicedata.application.port.out.ImportStagingPort;
import com.hmdp.servicedata.application.service.ConsumerIdentityResolutionPolicy;
import com.hmdp.servicedata.application.service.ServiceDataImportCommitException;
import com.hmdp.servicedata.application.service.ServiceDataImportCommitService;
import com.hmdp.servicedata.domain.model.Conversation;
import com.hmdp.servicedata.domain.model.Consumer;
import com.hmdp.servicedata.domain.model.Message;
import com.hmdp.servicedata.domain.model.ScopeRef;
import com.hmdp.servicedata.domain.repository.ConsumerAliasRepository;
import com.hmdp.servicedata.domain.repository.ConsumerRepository;
import com.hmdp.servicedata.domain.repository.ConversationRepository;
import com.hmdp.servicedata.domain.repository.MessageRepository;
import com.hmdp.servicedata.domain.repository.OrderSnapshotRepository;
import com.hmdp.servicedata.domain.repository.ServiceCaseRepository;
import com.hmdp.servicedata.domain.repository.SourceLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceDataImportCommitServiceTest {
    private static final ScopeRef SCOPE = new ScopeRef("tenant-a", "workspace-a");
    private static final Instant NOW = Instant.parse("2026-07-27T04:00:00Z");

    @Mock
    private ImportStagingPort staging;
    @Mock
    private ConsumerRepository consumers;
    @Mock
    private ConsumerAliasRepository aliases;
    @Mock
    private ConversationRepository conversations;
    @Mock
    private MessageRepository messages;
    @Mock
    private OrderSnapshotRepository orders;
    @Mock
    private ServiceCaseRepository serviceCases;
    @Mock
    private SourceLinkRepository links;

    private ServiceDataImportCommitService service;

    @BeforeEach
    void setUp() {
        service = new ServiceDataImportCommitService(staging, consumers, aliases,
                conversations, messages, orders, serviceCases, links,
                new ConsumerIdentityResolutionPolicy(),
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void writesConsumerAliasBeforeConversationsAndMergesSameAliasWithinChatScope() {
        StagedImportRows rows = new StagedImportRows();
        rows.addAlias(new ImportRows.ConsumerAliasRow("方***", "chat"));
        rows.addConversation(new ImportRows.ConversationRow(
                "S00001", "方***", "chat:S00001", 1, NOW, NOW));
        rows.addConversation(new ImportRows.ConversationRow(
                "S00002", "方***", "chat:S00002", 1, NOW, NOW));
        staged(rows);
        when(aliases.findByIdentity(any(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(aliases.insertIfAbsent(any())).thenReturn(true);
        when(conversations.findBySourceKey(any(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        var result = service.commit(SCOPE, "batch-1", "operator-1");

        assertThat(result.getCreated().getConsumerAliases()).isEqualTo(1);
        assertThat(result.getCreated().getConversations()).isEqualTo(2);
        ArgumentCaptor<Conversation> captured = ArgumentCaptor.forClass(Conversation.class);
        verify(conversations, org.mockito.Mockito.times(2)).insert(captured.capture(),
                anyString());
        assertThat(captured.getAllValues()).extracting(Conversation::getConsumerId)
                .containsOnly(captured.getAllValues().get(0).getConsumerId());
        InOrder order = inOrder(consumers, aliases, conversations);
        order.verify(consumers).insertIfAbsent(any(), anyString());
        order.verify(aliases).insertIfAbsent(any());
        order.verify(conversations, org.mockito.Mockito.times(2)).insert(any(), anyString());
    }

    @Test
    void identicalAliasesInDifferentSourceScopesDoNotMerge() {
        StagedImportRows rows = new StagedImportRows();
        rows.addAlias(new ImportRows.ConsumerAliasRow("same-alias", "chat"));
        rows.addAlias(new ImportRows.ConsumerAliasRow("same-alias", "orders"));
        staged(rows);
        when(aliases.findByIdentity(any(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(aliases.insertIfAbsent(any())).thenReturn(true);

        var result = service.commit(SCOPE, "batch-scopes", "operator-1");

        assertThat(result.getCreated().getConsumerAliases()).isEqualTo(2);
        ArgumentCaptor<Consumer> captured = ArgumentCaptor.forClass(Consumer.class);
        verify(consumers, org.mockito.Mockito.times(2))
                .insertIfAbsent(captured.capture(), anyString());
        assertThat(captured.getAllValues()).extracting(Consumer::getId)
                .doesNotHaveDuplicates();
    }

    @Test
    void consumerIdentityAlsoSeparatesSourceSystems() {
        ConsumerIdentityResolutionPolicy policy = new ConsumerIdentityResolutionPolicy();
        String aliasHash = policy.normalizedAliasHash("same-alias");

        Consumer first = policy.newConsumer(SCOPE, "source-a", "chat",
                aliasHash, "same-alias");
        Consumer second = policy.newConsumer(SCOPE, "source-b", "chat",
                aliasHash, "same-alias");

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void identicalOrderAndCaseContentIsSkipped() {
        StagedImportRows rows = orderAndCaseRows("PAID", "OPEN", BigDecimal.TEN);
        staged(rows);
        when(orders.existsByContent(any(), anyString(), anyString())).thenReturn(true);
        when(serviceCases.existsByContent(any(), anyString(), anyString())).thenReturn(true);

        var result = service.commit(SCOPE, "batch-skip", "operator-1");

        assertThat(result.getSkipped().getOrderSnapshots()).isEqualTo(1);
        assertThat(result.getSkipped().getServiceCases()).isEqualTo(1);
        assertThat(result.getCreated().total()).isZero();
        assertThat(result.getUpdated().total()).isZero();
        verify(orders, never()).insert(any());
        verify(serviceCases, never()).insert(any());
    }

    @Test
    void changedOrderAndCaseContentCreatesTheNextAppendOnlyVersions() {
        StagedImportRows rows = orderAndCaseRows("SHIPPED", "CLOSED", BigDecimal.valueOf(20));
        staged(rows);
        when(orders.existsByContent(any(), anyString(), anyString())).thenReturn(false);
        when(serviceCases.existsByContent(any(), anyString(), anyString())).thenReturn(false);
        when(orders.nextSnapshotSeq(any(), anyString())).thenReturn(2);
        when(serviceCases.nextCaseSeq(any(), anyString())).thenReturn(2);

        var result = service.commit(SCOPE, "batch-update", "operator-1");

        assertThat(result.getUpdated().getOrderSnapshots()).isEqualTo(1);
        assertThat(result.getUpdated().getServiceCases()).isEqualTo(1);
        verify(orders).insert(org.mockito.ArgumentMatchers.argThat(value ->
                value.getSnapshotSeq() == 2));
        verify(serviceCases).insert(org.mockito.ArgumentMatchers.argThat(value ->
                value.getCaseSeq() == 2));
    }

    @Test
    void changedMessageBehindTheSameSourceKeyIsRejectedRatherThanOverwritten() {
        StagedImportRows rows = new StagedImportRows();
        rows.addMessage(new ImportRows.MessageRow("S00001", 1, "msg-1", NOW,
                "CONSUMER", "方***", "new content", "TEXT", null, null, 2));
        staged(rows);
        Conversation conversation = conversation("conv-1", "S00001");
        when(conversations.findBySourceKey(any(), anyString(), anyString()))
                .thenReturn(Optional.of(conversation));
        when(messages.findBySourceKey(any(), anyString(), anyString())).thenReturn(Optional.of(
                new Message("message-1", SCOPE, "conv-1", "msg-1", "CONSUMER",
                        "方***", "old content", "TEXT", null, null, NOW, 1, "old-batch")));

        assertThatThrownBy(() -> service.commit(SCOPE, "batch-message", "operator-1"))
                .isInstanceOf(ServiceDataImportCommitException.class)
                .hasMessageNotContaining("new content")
                .hasMessageNotContaining("old content")
                .hasMessageNotContaining("方***");
        verify(messages, never()).insert(any());
    }

    @Test
    void identicalMessageUsesDatabaseTimestampPrecisionForIdempotency() {
        Instant sourceTime = Instant.parse("2026-07-27T04:00:00.123456Z");
        StagedImportRows rows = new StagedImportRows();
        rows.addMessage(new ImportRows.MessageRow("S00001", 1, "msg-1", sourceTime,
                "CONSUMER", "masked-alias", "same content", "TEXT",
                null, null, 2));
        staged(rows);
        when(conversations.findBySourceKey(any(), anyString(), anyString()))
                .thenReturn(Optional.of(conversation("conv-1", "S00001")));
        when(messages.findBySourceKey(any(), anyString(), anyString())).thenReturn(Optional.of(
                new Message("message-1", SCOPE, "conv-1", "msg-1", "CONSUMER",
                        "masked-alias", "same content", "TEXT", null, null,
                        Instant.parse("2026-07-27T04:00:00.123Z"), 1, "old-batch")));

        var result = service.commit(SCOPE, "batch-message-repeat", "operator-1");

        assertThat(result.getSkipped().getMessages()).isEqualTo(1);
        verify(messages, never()).insert(any());
    }

    @Test
    void stagingCountMismatchStopsBeforeAnyFactWriter() {
        StagedImportRows rows = new StagedImportRows();
        when(staging.loadForCommit(SCOPE, "batch-mismatch")).thenReturn(rows);
        when(staging.findCounts(SCOPE, "batch-mismatch")).thenReturn(
                new ServiceDataImportCounts(0, 0, 1, 0, 0, 0, 0));

        assertThatThrownBy(() -> service.commit(SCOPE, "batch-mismatch", "operator-1"))
                .isInstanceOf(ServiceDataImportCommitException.class);
        verify(consumers, never()).insertIfAbsent(any(), anyString());
        verify(conversations, never()).insert(any(), anyString());
        verify(messages, never()).insert(any());
    }

    private void staged(StagedImportRows rows) {
        when(staging.loadForCommit(any(), anyString())).thenReturn(rows);
        when(staging.findCounts(any(), anyString())).thenReturn(rows.counts());
    }

    private StagedImportRows orderAndCaseRows(String orderStatus, String caseStatus,
                                               BigDecimal amount) {
        StagedImportRows rows = new StagedImportRows();
        rows.addOrder(new ImportRows.OrderRow("000123", "S00001", "方***", "orders",
                "sku-1", "商品", 1, BigDecimal.ONE, amount, orderStatus, NOW, NOW,
                null, null, null, Collections.emptyMap(), 2));
        rows.addServiceCase(new ImportRows.ServiceCaseRow("case-1", "退款工单", "S00001",
                "000123", "方***", "cases:退款工单", caseStatus, "reason", "description",
                NOW, null, Collections.emptyMap(), 2));
        return rows;
    }

    private Conversation conversation(String id, String sourceId) {
        return new Conversation(id, SCOPE, "competition-workbook", sourceId, "consumer-1",
                "CHAT", null, NOW, NOW, 1, NOW, NOW, "a".repeat(64), "old-batch", 0);
    }
}
