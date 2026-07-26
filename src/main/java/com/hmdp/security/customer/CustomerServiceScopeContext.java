package com.hmdp.security.customer;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable request-scoped identity and scope facts for customer-service APIs.
 * Holds only technical scope data; no customer domain objects belong here.
 */
public final class CustomerServiceScopeContext {
    private final String userId;
    private final String tenantId;
    private final String workspaceId;
    private final Set<CustomerServicePermission> permissions;

    public CustomerServiceScopeContext(String userId, String tenantId, String workspaceId,
                                       Set<CustomerServicePermission> permissions) {
        this.userId = requireText(userId, "userId");
        this.tenantId = requireText(tenantId, "tenantId");
        this.workspaceId = requireText(workspaceId, "workspaceId");
        Set<CustomerServicePermission> copy = permissions == null || permissions.isEmpty()
                ? EnumSet.noneOf(CustomerServicePermission.class)
                : EnumSet.copyOf(permissions);
        this.permissions = Collections.unmodifiableSet(copy);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public String getUserId() {
        return userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public Set<CustomerServicePermission> getPermissions() {
        return permissions;
    }

    public boolean has(CustomerServicePermission permission) {
        return permissions.contains(Objects.requireNonNull(permission, "permission"));
    }
}
