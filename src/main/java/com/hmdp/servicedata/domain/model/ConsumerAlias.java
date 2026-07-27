package com.hmdp.servicedata.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/**
 * A consumer name as it appeared in one source scope. Aliases only merge within
 * {@code sourceSystem + sourceScope + normalizedAliasHash}; a nickname is never a
 * global consumer id, so merge confidence stays LIMITED by default and provenance
 * is preserved per alias.
 */
public final class ConsumerAlias {
    public static final String CONFIDENCE_LIMITED = "LIMITED";

    private final String id;
    private final ScopeRef scope;
    private final String consumerId;
    private final String sourceSystem;
    private final String sourceScope;
    private final String displayAlias;
    private final String normalizedAliasHash;
    private final String mergeConfidence;
    private final String provenanceJson;
    private final String importBatchId;

    public ConsumerAlias(String id, ScopeRef scope, String consumerId, String sourceSystem,
                         String sourceScope, String displayAlias, String mergeConfidence,
                         String importBatchId) {
        this(id, scope, consumerId, sourceSystem, sourceScope, displayAlias, mergeConfidence,
                null, importBatchId);
    }

    public ConsumerAlias(String id, ScopeRef scope, String consumerId, String sourceSystem,
                         String sourceScope, String displayAlias, String mergeConfidence,
                         String provenanceJson, String importBatchId) {
        this.id = ScopeRef.requireText(id, "id");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.consumerId = ScopeRef.requireText(consumerId, "consumerId");
        this.sourceSystem = ScopeRef.requireText(sourceSystem, "sourceSystem");
        this.sourceScope = ScopeRef.requireText(sourceScope, "sourceScope");
        this.displayAlias = ScopeRef.requireText(displayAlias, "displayAlias");
        this.normalizedAliasHash = normalizedHashOf(displayAlias);
        this.mergeConfidence = mergeConfidence == null || mergeConfidence.trim().isEmpty()
                ? CONFIDENCE_LIMITED : mergeConfidence.trim();
        this.provenanceJson = provenanceJson;
        this.importBatchId = importBatchId;
    }

    /** NFKC-normalized, trimmed, lower-cased alias hashed with SHA-256 (hex). */
    public static String normalizedHashOf(String alias) {
        String normalized = Normalizer.normalize(
                        ScopeRef.requireText(alias, "alias"), Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }

    public String getId() {
        return id;
    }

    public ScopeRef getScope() {
        return scope;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public String getSourceScope() {
        return sourceScope;
    }

    public String getDisplayAlias() {
        return displayAlias;
    }

    public String getNormalizedAliasHash() {
        return normalizedAliasHash;
    }

    public String getMergeConfidence() {
        return mergeConfidence;
    }

    public String getProvenanceJson() {
        return provenanceJson;
    }

    public String getImportBatchId() {
        return importBatchId;
    }
}
