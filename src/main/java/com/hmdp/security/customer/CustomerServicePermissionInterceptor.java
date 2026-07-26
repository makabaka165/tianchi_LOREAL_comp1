package com.hmdp.security.customer;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Scope and permission gate for {@code /api/v1/customer-service/**}.
 *
 * <p>Reuses login (sa-token), tenant/workspace membership facts ({@code ai_workspace_member})
 * and {@code sys_permission} codes, but never the AI domain security model. Order of checks:
 * login, explicit permission declaration, scope headers, active workspace membership,
 * permission code (or {@code *} wildcard). Non-member and missing permission are both
 * reported as {@code CS_PERMISSION_DENIED} so object existence is not leaked across scopes.
 */
@Component
public class CustomerServicePermissionInterceptor implements AsyncHandlerInterceptor {
    private final JdbcTemplate jdbcTemplate;

    public CustomerServicePermissionInterceptor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        if (!StpUtil.isLogin()) {
            return reject(response, 401, "CS_UNAUTHENTICATED");
        }
        HandlerMethod method = (HandlerMethod) handler;
        RequireCustomerServicePermission required = AnnotatedElementUtils.findMergedAnnotation(
                method.getMethod(), RequireCustomerServicePermission.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(
                    method.getBeanType(), RequireCustomerServicePermission.class);
        }
        if (required == null) {
            return reject(response, 403, "CS_PERMISSION_DECLARATION_REQUIRED");
        }
        String tenantId = header(request, "X-Tenant-Id");
        String workspaceId = header(request, "X-Workspace-Id");
        if (tenantId == null || workspaceId == null) {
            return reject(response, 400, "CS_CONTEXT_REQUIRED");
        }
        String userId = StpUtil.getLoginIdAsString();
        if (!isActiveMember(userId, tenantId, workspaceId)) {
            return reject(response, 403, "CS_PERMISSION_DENIED");
        }
        Set<CustomerServicePermission> granted = grantedPermissions();
        if (!granted.contains(required.value())) {
            return reject(response, 403, "CS_PERMISSION_DENIED");
        }
        CustomerServiceScopeContextHolder.set(
                new CustomerServiceScopeContext(userId, tenantId, workspaceId, granted));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        CustomerServiceScopeContextHolder.clear();
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response,
                                               Object handler) {
        CustomerServiceScopeContextHolder.clear();
    }

    private boolean isActiveMember(String userId, String tenantId, String workspaceId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(1) from ai_workspace_member wm "
                            + "join ai_workspace w on w.id = wm.workspace_id "
                            + "where wm.user_id = ? and wm.workspace_id = ? and w.tenant_id = ? "
                            + "and wm.status = 'ACTIVE' and wm.deleted = 0 and w.deleted = 0",
                    Integer.class, userId, workspaceId, tenantId);
            return count != null && count > 0;
        } catch (DataAccessException e) {
            return false;
        }
    }

    private Set<CustomerServicePermission> grantedPermissions() {
        List<String> codes = StpUtil.getPermissionList();
        if (codes == null || codes.isEmpty()) {
            return EnumSet.noneOf(CustomerServicePermission.class);
        }
        if (codes.contains("*")) {
            return EnumSet.allOf(CustomerServicePermission.class);
        }
        Set<CustomerServicePermission> granted = EnumSet.noneOf(CustomerServicePermission.class);
        for (CustomerServicePermission permission : CustomerServicePermission.values()) {
            if (codes.contains(permission.code())) {
                granted.add(permission);
            }
        }
        return granted;
    }

    private String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private boolean reject(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"code\":\"" + code + "\"}");
        return false;
    }
}
