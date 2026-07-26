package com.hmdp.servicedata.domain.model;

import java.util.Objects;

/**
 * A consumer identity under the limited merge policy: one consumer groups aliases that
 * share {@code sourceSystem + sourceScope + normalizedAliasHash}. Cross-source merging
 * never happens automatically.
 */
public final class Consumer {
    public static final String MERGE_POLICY_LIMITED = "LIMITED_SOURCE_SCOPE";

    private final String id;
    private final ScopeRef scope;
    private final String displayName;
    private final String mergePolicy;
    private final int version;

    public Consumer(String id, ScopeRef scope, String displayName, String mergePolicy, int version) {
        this.id = ScopeRef.requireText(id, "id");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.displayName = ScopeRef.requireText(displayName, "displayName");
        this.mergePolicy = mergePolicy == null || mergePolicy.trim().isEmpty()
                ? MERGE_POLICY_LIMITED : mergePolicy.trim();
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public ScopeRef getScope() {
        return scope;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getMergePolicy() {
        return mergePolicy;
    }

    public int getVersion() {
        return version;
    }
}
