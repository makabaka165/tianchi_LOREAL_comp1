package com.hmdp.security.customer;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.context.SaTokenContextForThreadLocal;
import cn.dev33.satoken.context.SaTokenContextForThreadLocalStorage;
import cn.dev33.satoken.servlet.model.SaRequestForServlet;
import cn.dev33.satoken.servlet.model.SaResponseForServlet;
import cn.dev33.satoken.servlet.model.SaStorageForServlet;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerServicePermissionInterceptorTest {
    private static final String MEMBER_WORKSPACE = "default";
    private static final String MEMBER_TENANT = "default";

    private SaTokenContext previousContext;
    private StpInterface previousStpInterface;
    private JdbcTemplate jdbc;
    private CustomerServicePermissionInterceptor interceptor;
    private List<String> permissionCodes;

    @RequireCustomerServicePermission(CustomerServicePermission.WORKSPACE_READ)
    static class AnnotatedController {
        public void list() {
        }
    }

    static class UnannotatedController {
        public void naked() {
        }
    }

    static class MethodAnnotatedController {
        @RequireCustomerServicePermission(CustomerServicePermission.DATA_IMPORT)
        public void preview() {
        }
    }

    @BeforeEach
    void setUp() {
        previousContext = SaManager.getSaTokenContext();
        previousStpInterface = SaManager.getStpInterface();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        SaManager.setSaTokenContext(new SaTokenContextForThreadLocal());
        SaTokenContextForThreadLocalStorage.setBox(
                new SaRequestForServlet(request),
                new SaResponseForServlet(response),
                new SaStorageForServlet(request));
        permissionCodes = new ArrayList<>();
        SaManager.setStpInterface(new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return new ArrayList<>(permissionCodes);
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return Collections.emptyList();
            }
        });
        jdbc = mock(JdbcTemplate.class);
        interceptor = new CustomerServicePermissionInterceptor(jdbc);
    }

    @AfterEach
    void tearDown() {
        try {
            if (StpUtil.isLogin()) {
                StpUtil.logout();
            }
        } catch (RuntimeException ignored) {
            // ignore cleanup failures in unit tests
        }
        CustomerServiceScopeContextHolder.clear();
        SaTokenContextForThreadLocalStorage.clearBox();
        SaManager.setSaTokenContext(previousContext);
        SaManager.setStpInterface(previousStpInterface);
    }

    private HandlerMethod handler(Object bean, String methodName) throws NoSuchMethodException {
        return new HandlerMethod(bean, bean.getClass().getMethod(methodName));
    }

    private MockHttpServletRequest scopedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/customer-service/conversations");
        request.addHeader("X-Tenant-Id", MEMBER_TENANT);
        request.addHeader("X-Workspace-Id", MEMBER_WORKSPACE);
        return request;
    }

    /** Membership exists only for (user, MEMBER_WORKSPACE, MEMBER_TENANT). */
    private void stubMembership(String userId) {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String user = invocation.getArgument(2, String.class);
                    String workspace = invocation.getArgument(3, String.class);
                    String tenant = invocation.getArgument(4, String.class);
                    return userId.equals(user)
                            && MEMBER_WORKSPACE.equals(workspace)
                            && MEMBER_TENANT.equals(tenant) ? 1 : 0;
                });
    }

    @Test
    void rejectsAnonymousRequest() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(scopedRequest(), response,
                handler(new AnnotatedController(), "list"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("CS_UNAUTHENTICATED");
    }

    @Test
    void rejectsHandlerWithoutPermissionDeclaration() throws Exception {
        StpUtil.login(11L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(scopedRequest(), response,
                handler(new UnannotatedController(), "naked"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CS_PERMISSION_DECLARATION_REQUIRED");
    }

    @Test
    void rejectsMissingScopeHeaders() throws Exception {
        StpUtil.login(11L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/customer-service/conversations");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response,
                handler(new AnnotatedController(), "list"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("CS_CONTEXT_REQUIRED");
    }

    @Test
    void rejectsBlankScopeHeader() throws Exception {
        StpUtil.login(11L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/customer-service/conversations");
        request.addHeader("X-Tenant-Id", "   ");
        request.addHeader("X-Workspace-Id", MEMBER_WORKSPACE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response,
                handler(new AnnotatedController(), "list"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("CS_CONTEXT_REQUIRED");
    }

    @Test
    void rejectsNonMemberEvenWithPermissionCode() throws Exception {
        StpUtil.login(12L);
        stubMembership("999");
        permissionCodes.add("cs:workspace:read");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(scopedRequest(), response,
                handler(new AnnotatedController(), "list"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CS_PERMISSION_DENIED");
        assertThat(CustomerServiceScopeContextHolder.current()).isEmpty();
    }

    @Test
    void rejectsCrossWorkspaceScope() throws Exception {
        StpUtil.login(13L);
        stubMembership("13");
        permissionCodes.add("cs:workspace:read");
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/customer-service/conversations");
        request.addHeader("X-Tenant-Id", MEMBER_TENANT);
        request.addHeader("X-Workspace-Id", "other-workspace");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response,
                handler(new AnnotatedController(), "list"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CS_PERMISSION_DENIED");
    }

    @Test
    void rejectsMemberWithoutPermissionCode() throws Exception {
        StpUtil.login(14L);
        stubMembership("14");
        permissionCodes.add("cs:risk:read");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(scopedRequest(), response,
                handler(new AnnotatedController(), "list"));

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CS_PERMISSION_DENIED");
    }

    @Test
    void allowsMemberWithPermissionCodeAndPopulatesContext() throws Exception {
        StpUtil.login(15L);
        stubMembership("15");
        permissionCodes.addAll(Arrays.asList("cs:workspace:read", "cs:risk:read"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(scopedRequest(), response,
                handler(new AnnotatedController(), "list"));

        assertThat(allowed).isTrue();
        CustomerServiceScopeContext context = CustomerServiceScopeContextHolder.require();
        assertThat(context.getUserId()).isEqualTo("15");
        assertThat(context.getTenantId()).isEqualTo(MEMBER_TENANT);
        assertThat(context.getWorkspaceId()).isEqualTo(MEMBER_WORKSPACE);
        assertThat(context.has(CustomerServicePermission.WORKSPACE_READ)).isTrue();
        assertThat(context.has(CustomerServicePermission.RISK_READ)).isTrue();
        assertThat(context.has(CustomerServicePermission.RISK_MANAGE)).isFalse();
    }

    @Test
    void allowsAdminWildcard() throws Exception {
        StpUtil.login(16L);
        stubMembership("16");
        permissionCodes.add("*");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(scopedRequest(), response,
                handler(new AnnotatedController(), "list"));

        assertThat(allowed).isTrue();
        CustomerServiceScopeContext context = CustomerServiceScopeContextHolder.require();
        assertThat(context.getPermissions())
                .containsExactlyInAnyOrder(CustomerServicePermission.values());
    }

    @Test
    void methodLevelAnnotationTakesPrecedence() throws Exception {
        StpUtil.login(17L);
        stubMembership("17");
        permissionCodes.add("cs:data:import");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(scopedRequest(), response,
                handler(new MethodAnnotatedController(), "preview"));

        assertThat(allowed).isTrue();
    }

    @Test
    void clearsThreadLocalOnCompletionAndAsyncHandover() throws Exception {
        StpUtil.login(18L);
        stubMembership("18");
        permissionCodes.add("cs:workspace:read");
        MockHttpServletRequest request = scopedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = handler(new AnnotatedController(), "list");

        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
        assertThat(CustomerServiceScopeContextHolder.current()).isPresent();

        interceptor.afterCompletion(request, response, handler, null);
        assertThat(CustomerServiceScopeContextHolder.current()).isEmpty();

        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
        interceptor.afterConcurrentHandlingStarted(request, response, handler);
        assertThat(CustomerServiceScopeContextHolder.current()).isEmpty();
    }

    @Test
    void passesThroughNonHandlerMethod() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(scopedRequest(), response, new Object());

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
