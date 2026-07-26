package com.hmdp.security.customer;

import java.util.Optional;

/**
 * Customer-service permissions carry their external permission codes directly.
 * They are intentionally decoupled from the AI domain security model so that
 * servicedata/serviceassist/riskops never depend on {@code com.hmdp.ai.domain.security}.
 */
public enum CustomerServicePermission {
    DATA_IMPORT("cs:data:import"),
    WORKSPACE_READ("cs:workspace:read"),
    ASSIST_REQUEST("cs:assist:request"),
    SUGGESTION_DECIDE("cs:suggestion:decide"),
    RISK_READ("cs:risk:read"),
    RISK_MANAGE("cs:risk:manage");

    private final String code;

    CustomerServicePermission(String code) {
        this.code = code;
    }

    /** External permission code as stored in {@code sys_permission.permission_code}. */
    public String code() {
        return code;
    }

    public static Optional<CustomerServicePermission> fromCode(String code) {
        for (CustomerServicePermission permission : values()) {
            if (permission.code.equals(code)) {
                return Optional.of(permission);
            }
        }
        return Optional.empty();
    }
}
