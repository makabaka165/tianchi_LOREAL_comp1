package com.hmdp.security.customer;

import java.util.Optional;

/**
 * ThreadLocal holder for {@link CustomerServiceScopeContext}. Populated only by
 * {@link CustomerServicePermissionInterceptor} for the duration of one request thread;
 * always cleared on completion and on async dispatch handover.
 */
public final class CustomerServiceScopeContextHolder {
    private static final ThreadLocal<CustomerServiceScopeContext> CONTEXT = new ThreadLocal<>();

    private CustomerServiceScopeContextHolder() {
    }

    public static void set(CustomerServiceScopeContext context) {
        if (context == null) {
            CONTEXT.remove();
        } else {
            CONTEXT.set(context);
        }
    }

    public static Optional<CustomerServiceScopeContext> current() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static CustomerServiceScopeContext require() {
        CustomerServiceScopeContext context = CONTEXT.get();
        if (context == null) {
            throw new IllegalStateException("customer service scope context is not available");
        }
        return context;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
