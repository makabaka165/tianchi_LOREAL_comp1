package com.hmdp.servicedata.application.contract;

import com.hmdp.servicedata.domain.model.ScopeRef;

/** Authenticated operator and tenant/workspace scope for an import command. */
public final class ServiceDataImportScope {
    private final String tenantId;
    private final String workspaceId;
    private final String actorId;

    public ServiceDataImportScope(String tenantId, String workspaceId, String actorId) {
        this.tenantId = ScopeRef.requireText(tenantId, "tenantId");
        this.workspaceId = ScopeRef.requireText(workspaceId, "workspaceId");
        this.actorId = ScopeRef.requireText(actorId, "actorId");
    }

    public ScopeRef toScopeRef() {
        return new ScopeRef(tenantId, workspaceId);
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getActorId() {
        return actorId;
    }
}
