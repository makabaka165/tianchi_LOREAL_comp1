package com.hmdp.security.customer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Guarantees a stable {@code CS_FEATURE_DISABLED} response (never a bare 404 or blank
 * page) for {@code /api/v1/customer-service/**} while the vertical is switched off.
 * Runs as a filter because interceptors are skipped when no handler bean exists.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class CustomerServiceFeatureGateFilter extends OncePerRequestFilter {
    private static final String CUSTOMER_SERVICE_PREFIX = "/api/v1/customer-service/";

    private final boolean customerServiceEnabled;

    public CustomerServiceFeatureGateFilter(
            @Value("${hmdp.customer-service.enabled:false}") boolean customerServiceEnabled) {
        this.customerServiceEnabled = customerServiceEnabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !(uri.startsWith(CUSTOMER_SERVICE_PREFIX)
                || uri.equals("/api/v1/customer-service"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (customerServiceEnabled) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(503);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"code\":\"CS_FEATURE_DISABLED\","
                        + "\"errorMsg\":\"customer service module is disabled\"}");
    }
}
