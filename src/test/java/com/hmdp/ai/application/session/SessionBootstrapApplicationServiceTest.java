package com.hmdp.ai.application.session;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.context.SaTokenContextForThreadLocal;
import cn.dev33.satoken.context.SaTokenContextForThreadLocalStorage;
import cn.dev33.satoken.servlet.model.SaRequestForServlet;
import cn.dev33.satoken.servlet.model.SaResponseForServlet;
import cn.dev33.satoken.servlet.model.SaStorageForServlet;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.hmdp.ai.application.dto.session.SessionBootstrapResponse;
import com.hmdp.ai.application.security.AiAuthorizationService;
import com.hmdp.ai.domain.security.AiPermission;
import com.hmdp.ai.domain.security.AuthorizationContext;
import com.hmdp.ai.shared.exception.AiPlatformException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionBootstrapApplicationServiceTest {
    private SaTokenContext previousContext;
    private StpInterface previousStpInterface;
    private List<String> permissionCodes;

    @BeforeEach
    void setUpSaToken() {
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
    }

    @AfterEach
    void tearDownSaToken() {
        try {
            if (StpUtil.isLogin()) {
                StpUtil.logout();
            }
        } catch (RuntimeException ignored) {
            // ignore cleanup failures in unit tests
        }
        SaTokenContextForThreadLocalStorage.clearBox();
        SaManager.setSaTokenContext(previousContext);
        SaManager.setStpInterface(previousStpInterface);
    }

    @SuppressWarnings("unchecked")
    private void stubUserRow(JdbcTemplate jdbc, long userId, String nickName, String icon) {
        when(jdbc.query(contains("from tb_user"), any(RowMapper.class), eq(userId)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("id")).thenReturn(String.valueOf(userId));
                    when(rs.getString("nick_name")).thenReturn(nickName);
                    when(rs.getString("icon")).thenReturn(icon);
                    return Collections.singletonList(mapper.mapRow(rs, 0));
                });
    }

    @SuppressWarnings("unchecked")
    private void stubMembershipRow(JdbcTemplate jdbc, String userId, String role, String status) {
        when(jdbc.query(contains("ai_workspace_member"), any(RowMapper.class), eq(userId)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("tenant_id")).thenReturn("default");
                    when(rs.getString("tenant_name")).thenReturn("Default tenant");
                    when(rs.getString("workspace_id")).thenReturn("default");
                    when(rs.getString("workspace_name")).thenReturn("Default workspace");
                    when(rs.getString("role")).thenReturn(role);
                    when(rs.getString("status")).thenReturn(status);
                    return Collections.singletonList(mapper.mapRow(rs, 0));
                });
    }

    @Test
    void returnsMembershipsAndDefaultScopeForActiveMember() {
        StpUtil.login(1L);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubUserRow(jdbc, 1L, "owner", "icon.png");
        stubMembershipRow(jdbc, "1", "OWNER", "ACTIVE");

        AiAuthorizationService authorization = mock(AiAuthorizationService.class);
        when(authorization.authorize("1", "default", "default"))
                .thenReturn(new AuthorizationContext(EnumSet.of(AiPermission.ADMIN, AiPermission.AGENT_RUN)));

        SessionBootstrapApplicationService service =
                new SessionBootstrapApplicationService(jdbc, authorization);

        SessionBootstrapResponse response = service.bootstrap();

        assertThat(response.getUser().getId()).isEqualTo("1");
        assertThat(response.getUser().getNickName()).isEqualTo("owner");
        assertThat(response.getMemberships()).hasSize(1);
        assertThat(response.getMemberships().get(0).isDefault()).isTrue();
        assertThat(response.getMemberships().get(0).getPermissions()).contains("AGENT_RUN", "ADMIN");
        assertThat(response.getDefaultScope().getTenantId()).isEqualTo("default");
        assertThat(response.getDefaultScope().getWorkspaceId()).isEqualTo("default");
    }

    @Test
    void returnsEmptyMembershipsWithoutDefaultScope() {
        StpUtil.login(2L);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubUserRow(jdbc, 2L, "guest", "");
        when(jdbc.query(contains("ai_workspace_member"), any(RowMapper.class), eq("2")))
                .thenReturn(Collections.emptyList());
        AiAuthorizationService authorization = mock(AiAuthorizationService.class);

        SessionBootstrapApplicationService service =
                new SessionBootstrapApplicationService(jdbc, authorization);

        SessionBootstrapResponse response = service.bootstrap();

        assertThat(response.getMemberships()).isEmpty();
        assertThat(response.getDefaultScope()).isNull();
    }

    @Test
    void rejectsInactiveMembershipFromDefaultScope() {
        StpUtil.login(3L);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubUserRow(jdbc, 3L, "inactive", "");
        stubMembershipRow(jdbc, "3", "RUNNER", "DISABLED");
        AiAuthorizationService authorization = mock(AiAuthorizationService.class);

        SessionBootstrapApplicationService service =
                new SessionBootstrapApplicationService(jdbc, authorization);

        SessionBootstrapResponse response = service.bootstrap();

        assertThat(response.getMemberships()).hasSize(1);
        assertThat(response.getMemberships().get(0).getPermissions()).isEmpty();
        assertThat(response.getMemberships().get(0).isDefault()).isFalse();
        assertThat(response.getDefaultScope()).isNull();
    }

    @Test
    void exposesCustomerServicePermissionCodesForActiveMembership() {
        StpUtil.login(6L);
        permissionCodes.add("cs:workspace:read");
        permissionCodes.add("cs:risk:read");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubUserRow(jdbc, 6L, "agent", "");
        stubMembershipRow(jdbc, "6", "RUNNER", "ACTIVE");
        AiAuthorizationService authorization = mock(AiAuthorizationService.class);
        when(authorization.authorize("6", "default", "default"))
                .thenReturn(new AuthorizationContext(EnumSet.of(AiPermission.AGENT_RUN)));

        SessionBootstrapApplicationService service =
                new SessionBootstrapApplicationService(jdbc, authorization);

        SessionBootstrapResponse response = service.bootstrap();

        assertThat(response.getMemberships().get(0).getPermissions())
                .contains("AGENT_RUN", "cs:workspace:read", "cs:risk:read")
                .doesNotContain("cs:risk:manage");
    }

    @Test
    void wildcardPermissionExposesAllCustomerServiceCodes() {
        StpUtil.login(7L);
        permissionCodes.add("*");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubUserRow(jdbc, 7L, "admin", "");
        stubMembershipRow(jdbc, "7", "OWNER", "ACTIVE");
        AiAuthorizationService authorization = mock(AiAuthorizationService.class);
        when(authorization.authorize("7", "default", "default"))
                .thenReturn(new AuthorizationContext(EnumSet.of(AiPermission.ADMIN)));

        SessionBootstrapApplicationService service =
                new SessionBootstrapApplicationService(jdbc, authorization);

        SessionBootstrapResponse response = service.bootstrap();

        assertThat(response.getMemberships().get(0).getPermissions()).contains(
                "cs:data:import", "cs:workspace:read", "cs:assist:request",
                "cs:suggestion:decide", "cs:risk:read", "cs:risk:manage");
    }

    @Test
    void rejectsUnknownUserAsUnauthorized() {
        StpUtil.login(9L);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(9L)))
                .thenReturn(Collections.emptyList());
        AiAuthorizationService authorization = mock(AiAuthorizationService.class);
        SessionBootstrapApplicationService service =
                new SessionBootstrapApplicationService(jdbc, authorization);

        assertThatThrownBy(service::bootstrap).isInstanceOf(AiPlatformException.class);
    }

    @Test
    void rejectsAnonymousBootstrap() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AiAuthorizationService authorization = mock(AiAuthorizationService.class);
        SessionBootstrapApplicationService service =
                new SessionBootstrapApplicationService(jdbc, authorization);

        assertThatThrownBy(service::bootstrap).isInstanceOf(AiPlatformException.class);
    }
}
