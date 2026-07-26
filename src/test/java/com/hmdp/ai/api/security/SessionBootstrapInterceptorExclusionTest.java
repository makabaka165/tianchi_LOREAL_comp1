package com.hmdp.ai.api.security;

import com.hmdp.ai.application.security.AiAuthorizationService;
import com.hmdp.config.MvcConfig;
import com.hmdp.security.customer.CustomerServicePermissionInterceptor;
import com.hmdp.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SessionBootstrapInterceptorExclusionTest {

    private List<InterceptorRegistration> registrations(InterceptorRegistry registry) throws Exception {
        Field registrationsField = InterceptorRegistry.class.getDeclaredField("registrations");
        registrationsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<InterceptorRegistration> result =
                (List<InterceptorRegistration>) registrationsField.get(registry);
        return result;
    }

    private Object interceptorOf(InterceptorRegistration registration) throws Exception {
        Field interceptorField = InterceptorRegistration.class.getDeclaredField("interceptor");
        interceptorField.setAccessible(true);
        return interceptorField.get(registration);
    }

    @SuppressWarnings("unchecked")
    private List<String> patternsOf(InterceptorRegistration registration, String fieldName) throws Exception {
        Field field = InterceptorRegistration.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (List<String>) field.get(registration);
    }

    @Test
    void bootstrapAndCustomerServicePathsAreExcludedFromAiPermissionInterceptor() throws Exception {
        AiPermissionInterceptor aiInterceptor = new AiPermissionInterceptor(mock(AiAuthorizationService.class));
        CustomerServicePermissionInterceptor csInterceptor =
                new CustomerServicePermissionInterceptor(mock(JdbcTemplate.class));
        MvcConfig config = new MvcConfig(mock(IUserService.class), aiInterceptor, csInterceptor);
        InterceptorRegistry registry = new InterceptorRegistry();
        config.addInterceptors(registry);

        boolean bootstrapExcluded = false;
        boolean customerServiceExcludedFromAi = false;
        boolean customerServiceRegistered = false;
        for (InterceptorRegistration registration : registrations(registry)) {
            Object interceptor = interceptorOf(registration);
            if (interceptor == aiInterceptor) {
                List<String> excludes = patternsOf(registration, "excludePatterns");
                bootstrapExcluded = excludes != null && excludes.contains("/api/v1/session/bootstrap");
                customerServiceExcludedFromAi = excludes != null
                        && excludes.contains("/api/v1/customer-service/**");
            }
            if (interceptor == csInterceptor) {
                List<String> includes = patternsOf(registration, "includePatterns");
                customerServiceRegistered = includes != null
                        && includes.contains("/api/v1/customer-service/**");
            }
        }

        assertThat(bootstrapExcluded)
                .as("bootstrap must not require tenant/workspace headers")
                .isTrue();
        assertThat(customerServiceExcludedFromAi)
                .as("customer-service paths must not be double-guarded by the AI interceptor")
                .isTrue();
        assertThat(customerServiceRegistered)
                .as("customer-service paths must be guarded by the customer scope interceptor")
                .isTrue();
    }
}
