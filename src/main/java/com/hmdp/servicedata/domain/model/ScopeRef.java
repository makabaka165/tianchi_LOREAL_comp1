package com.hmdp.servicedata.domain.model;

import java.util.Objects;

/** Tenant/workspace ownership of every service-data fact. */
public final class ScopeRef {
    private final String tenantId;
    private final String workspaceId;

    public ScopeRef(String tenantId, String workspaceId) {
        this.tenantId = requireText(tenantId, "tenantId");
        this.workspaceId = requireText(workspaceId, "workspaceId");
    }

    static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScopeRef)) {
            return false;
        }
        ScopeRef that = (ScopeRef) other;
        return tenantId.equals(that.tenantId) && workspaceId.equals(that.workspaceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, workspaceId);
    }

    @Override
    public String toString() {
        return tenantId + "/" + workspaceId;
    }
}
