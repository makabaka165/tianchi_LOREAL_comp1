package com.hmdp.servicedata.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Append-only versioned order fact. The order number is a String end to end — leading
 * zeros and long digits are preserved exactly as the source displayed them. Content
 * changes create a new snapshot version; existing rows are never overwritten.
 */
public final class OrderSnapshot {
    private final String id;
    private final ScopeRef scope;
    private final String orderNo;
    private final int snapshotSeq;
    private final String sourceSystem;
    private final String sourceKey;
    private final String orderStatus;
    private final String productName;
    private final String sku;
    private final Integer quantity;
    private final BigDecimal amount;
    private final String currency;
    private final Instant orderedAt;
    private final Instant paidAt;
    private final Instant shippedAt;
    private final Instant receivedAt;
    private final String logisticsNo;
    private final String logisticsCompany;
    private final int detailSchemaVersion;
    private final String detailJson;
    private final String contentHash;
    private final String importBatchId;

    public OrderSnapshot(String id, ScopeRef scope, String orderNo, int snapshotSeq,
                         String sourceSystem, String sourceKey, String orderStatus,
                         String productName, String sku, Integer quantity, BigDecimal amount,
                         String currency, Instant orderedAt, Instant paidAt, Instant shippedAt,
                         Instant receivedAt, String logisticsNo, String logisticsCompany,
                         int detailSchemaVersion, String detailJson, String contentHash,
                         String importBatchId) {
        this.id = ScopeRef.requireText(id, "id");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.orderNo = ScopeRef.requireText(orderNo, "orderNo");
        if (snapshotSeq < 1) {
            throw new IllegalArgumentException("snapshotSeq starts at 1");
        }
        this.snapshotSeq = snapshotSeq;
        this.sourceSystem = ScopeRef.requireText(sourceSystem, "sourceSystem");
        this.sourceKey = sourceKey;
        this.orderStatus = orderStatus;
        this.productName = productName;
        this.sku = sku;
        this.quantity = quantity;
        this.amount = amount;
        this.currency = currency;
        this.orderedAt = orderedAt;
        this.paidAt = paidAt;
        this.shippedAt = shippedAt;
        this.receivedAt = receivedAt;
        this.logisticsNo = logisticsNo;
        this.logisticsCompany = logisticsCompany;
        if (detailSchemaVersion < 1) {
            throw new IllegalArgumentException("detailSchemaVersion starts at 1");
        }
        this.detailSchemaVersion = detailSchemaVersion;
        this.detailJson = detailJson;
        this.contentHash = ImportBatch.requireSha256(contentHash);
        this.importBatchId = importBatchId;
    }

    public String getId() {
        return id;
    }

    public ScopeRef getScope() {
        return scope;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public int getSnapshotSeq() {
        return snapshotSeq;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public String getProductName() {
        return productName;
    }

    public String getSku() {
        return sku;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getOrderedAt() {
        return orderedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getLogisticsNo() {
        return logisticsNo;
    }

    public String getLogisticsCompany() {
        return logisticsCompany;
    }

    public int getDetailSchemaVersion() {
        return detailSchemaVersion;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getImportBatchId() {
        return importBatchId;
    }
}
