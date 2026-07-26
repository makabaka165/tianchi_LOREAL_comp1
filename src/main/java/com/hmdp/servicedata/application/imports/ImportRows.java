package com.hmdp.servicedata.application.imports;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed intermediate records produced by the workbook parser. No generic
 * Map&lt;String,Object&gt; crosses the application boundary; every field the importer
 * needs is explicit, and sheet-specific extras travel in a bounded detail map that is
 * serialized to detail_json at commit time. Evaluation label columns never reach these
 * types — the parser drops them at the cell level.
 */
public final class ImportRows {

    private ImportRows() {
    }

    public static final class ConversationRow {
        public final String sourceConversationId;
        public final String consumerAlias;
        public final String sourceScope;
        public final int messageCount;
        public final Instant firstMessageAt;
        public final Instant lastMessageAt;

        public ConversationRow(String sourceConversationId, String consumerAlias,
                               String sourceScope, int messageCount, Instant firstMessageAt,
                               Instant lastMessageAt) {
            this.sourceConversationId = sourceConversationId;
            this.consumerAlias = consumerAlias;
            this.sourceScope = sourceScope;
            this.messageCount = messageCount;
            this.firstMessageAt = firstMessageAt;
            this.lastMessageAt = lastMessageAt;
        }
    }

    public static final class MessageRow {
        public final String sourceConversationId;
        public final int sourceSequence;
        public final String sourceMessageId;
        public final Instant sentAt;
        public final String senderRole;
        public final String senderAlias;
        public final String content;
        public final String contentType;
        public final String mediaPath;
        public final String mediaStatus;
        public final int rowNo;

        public MessageRow(String sourceConversationId, int sourceSequence, String sourceMessageId,
                          Instant sentAt, String senderRole, String senderAlias, String content,
                          String contentType, String mediaPath, String mediaStatus, int rowNo) {
            this.sourceConversationId = sourceConversationId;
            this.sourceSequence = sourceSequence;
            this.sourceMessageId = sourceMessageId;
            this.sentAt = sentAt;
            this.senderRole = senderRole;
            this.senderAlias = senderAlias;
            this.content = content;
            this.contentType = contentType;
            this.mediaPath = mediaPath;
            this.mediaStatus = mediaStatus;
            this.rowNo = rowNo;
        }
    }

    public static final class OrderRow {
        public final String orderNo;
        public final String sourceConversationId;
        public final String consumerAlias;
        public final String sourceScope;
        public final String sku;
        public final String productName;
        public final Integer quantity;
        public final BigDecimal unitPrice;
        public final BigDecimal paidAmount;
        public final String orderStatus;
        public final Instant orderedAt;
        public final Instant paidAt;
        public final Instant shippedAt;
        public final String logisticsCompany;
        public final String logisticsNo;
        public final Map<String, String> detail;
        public final int rowNo;

        public OrderRow(String orderNo, String sourceConversationId, String consumerAlias,
                        String sourceScope, String sku, String productName, Integer quantity,
                        BigDecimal unitPrice, BigDecimal paidAmount, String orderStatus,
                        Instant orderedAt, Instant paidAt, Instant shippedAt,
                        String logisticsCompany, String logisticsNo, Map<String, String> detail,
                        int rowNo) {
            this.orderNo = orderNo;
            this.sourceConversationId = sourceConversationId;
            this.consumerAlias = consumerAlias;
            this.sourceScope = sourceScope;
            this.sku = sku;
            this.productName = productName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.paidAmount = paidAmount;
            this.orderStatus = orderStatus;
            this.orderedAt = orderedAt;
            this.paidAt = paidAt;
            this.shippedAt = shippedAt;
            this.logisticsCompany = logisticsCompany;
            this.logisticsNo = logisticsNo;
            this.detail = detail == null ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(detail));
            this.rowNo = rowNo;
        }
    }

    public static final class ServiceCaseRow {
        public final String caseNo;
        public final String caseType;
        public final String sourceConversationId;
        public final String orderNo;
        public final String consumerAlias;
        public final String sourceScope;
        public final String caseStatus;
        public final String reason;
        public final String description;
        public final Instant openedAt;
        public final Instant closedAt;
        public final Map<String, String> detail;
        public final int rowNo;

        public ServiceCaseRow(String caseNo, String caseType, String sourceConversationId,
                              String orderNo, String consumerAlias, String sourceScope,
                              String caseStatus, String reason, String description,
                              Instant openedAt, Instant closedAt, Map<String, String> detail,
                              int rowNo) {
            this.caseNo = caseNo;
            this.caseType = caseType;
            this.sourceConversationId = sourceConversationId;
            this.orderNo = orderNo;
            this.consumerAlias = consumerAlias;
            this.sourceScope = sourceScope;
            this.caseStatus = caseStatus;
            this.reason = reason;
            this.description = description;
            this.openedAt = openedAt;
            this.closedAt = closedAt;
            this.detail = detail == null ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(detail));
            this.rowNo = rowNo;
        }
    }

    public static final class SourceLinkRow {
        public final String linkType;
        public final String fromConversationId;
        public final String toRef;
        public final boolean quarantined;

        public SourceLinkRow(String linkType, String fromConversationId, String toRef,
                             boolean quarantined) {
            this.linkType = linkType;
            this.fromConversationId = fromConversationId;
            this.toRef = toRef;
            this.quarantined = quarantined;
        }
    }

    public static final class ConsumerAliasRow {
        public final String displayAlias;
        public final String sourceScope;

        public ConsumerAliasRow(String displayAlias, String sourceScope) {
            this.displayAlias = displayAlias;
            this.sourceScope = sourceScope;
        }
    }
}
