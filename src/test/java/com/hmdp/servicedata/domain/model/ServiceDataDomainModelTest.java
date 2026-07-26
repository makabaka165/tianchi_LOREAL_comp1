package com.hmdp.servicedata.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceDataDomainModelTest {
    private static final ScopeRef SCOPE = new ScopeRef("default", "default");
    private static final String HASH = "b5ac027e863c5580dab39c8f459e4698d65e9fbec29832c9915448f2087307b7";

    @Test
    void orderNumbersKeepLeadingZerosAsStrings() {
        OrderSnapshot snapshot = new OrderSnapshot("snap-1", SCOPE, "0007202606150001", 1,
                "competition-workbook", "orders:12", "已发货", "精华液", "SKU-01", 1,
                new BigDecimal("329.00"), "CNY", Instant.parse("2026-06-15T08:00:00Z"),
                null, null, null, "SF000012345678", "顺丰", 1, null, HASH, "batch-1");
        assertThat(snapshot.getOrderNo()).isEqualTo("0007202606150001");
        assertThat(snapshot.getLogisticsNo()).startsWith("SF");
    }

    @Test
    void orderSnapshotRequiresContentHashAndPositiveSeq() {
        assertThatThrownBy(() -> new OrderSnapshot("snap-2", SCOPE, "1001", 0,
                "competition-workbook", null, null, null, null, null, null, null,
                null, null, null, null, null, null, 1, null, HASH, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderSnapshot("snap-3", SCOPE, "1001", 1,
                "competition-workbook", null, null, null, null, null, null, null,
                null, null, null, null, null, null, 1, null, "bad-hash", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void messageRequiresContentOrMedia() {
        assertThatThrownBy(() -> new Message("m-1", SCOPE, "conv-1", "src-1", "CONSUMER",
                null, "  ", "TEXT", null, null, null, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mediaOnlyMessageCarriesMissingMediaStatus() {
        Message message = new Message("m-2", SCOPE, "conv-1", "src-2", "CONSUMER", "小美",
                null, "IMAGE", "img/S00082/photo1.jpg", Message.MEDIA_STATUS_MISSING,
                null, 5, "batch-1");
        assertThat(message.isMediaMissing()).isTrue();
        assertThat(message.getSourceSequence()).isEqualTo(5);
    }

    @Test
    void aliasNormalizationIsStableAcrossWidthAndCase() {
        ConsumerAlias fullWidth = new ConsumerAlias("a-1", SCOPE, "c-1",
                "competition-workbook", "sheet:conversations", "Ｍｅｉｍｅｉ　", null, "batch-1");
        ConsumerAlias plain = new ConsumerAlias("a-2", SCOPE, "c-1",
                "competition-workbook", "sheet:conversations", "meimei", null, "batch-1");
        assertThat(fullWidth.getNormalizedAliasHash()).isEqualTo(plain.getNormalizedAliasHash());
        assertThat(fullWidth.getNormalizedAliasHash()).matches("[0-9a-f]{64}");
        assertThat(fullWidth.getMergeConfidence()).isEqualTo(ConsumerAlias.CONFIDENCE_LIMITED);
    }

    @Test
    void differentAliasesHashDifferently() {
        assertThat(ConsumerAlias.normalizedHashOf("meimei"))
                .isNotEqualTo(ConsumerAlias.normalizedHashOf("meimei2"));
    }

    @Test
    void sourceLinkValidatesEndpoints() {
        SourceLink link = new SourceLink("l-1", SCOPE, SourceLinkType.CONVERSATION_ORDER,
                "conv-1", "0007202606150001", "HIGH", "batch-1");
        assertThat(link.getLinkType()).isEqualTo(SourceLinkType.CONVERSATION_ORDER);
        assertThatThrownBy(() -> new SourceLink("l-2", SCOPE,
                SourceLinkType.CONVERSATION_CASE, "conv-1", " ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conversationRequiresSourceIdentityAndConsumer() {
        assertThatThrownBy(() -> new Conversation("conv-1", SCOPE, "competition-workbook",
                " ", "c-1", null, null, null, null, 0, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        Conversation conversation = new Conversation("conv-1", SCOPE, "competition-workbook",
                "S00082", "c-1", "ONLINE", "OPEN", null, null, 12, null, null, null, "batch-1");
        assertThat(conversation.getSourceConversationId()).isEqualTo("S00082");
    }

    @Test
    void serviceCaseKeepsStringNumbersAndVersioning() {
        ServiceCase serviceCase = new ServiceCase("case-1", SCOPE, "GD0001", 2,
                "competition-workbook", "cases:7", "退款", "处理中", "HIGH",
                "0007202606150001", null, null, "描述", null, 1, null, HASH, "batch-1");
        assertThat(serviceCase.getCaseNo()).isEqualTo("GD0001");
        assertThat(serviceCase.getCaseSeq()).isEqualTo(2);
        assertThat(serviceCase.getOrderNo()).isEqualTo("0007202606150001");
    }
}
