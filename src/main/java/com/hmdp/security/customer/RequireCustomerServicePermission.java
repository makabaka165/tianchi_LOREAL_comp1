package com.hmdp.security.customer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the customer-service permission required by a handler. Every handler under
 * {@code /api/v1/customer-service/**} must carry this annotation on the method or the
 * controller class; {@link CustomerServicePermissionInterceptor} rejects undeclared handlers.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireCustomerServicePermission {
    CustomerServicePermission value();
}
