package com.hmdp.serviceassist.infrastructure.config;

import com.hmdp.security.customer.CustomerServiceFeatureGateFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the competition profile configuration surface: property binding, feature
 * gate behaviour when the vertical is off, and health reporting. The prod+DEMO_FIXTURE
 * startup rejection is covered by SecurityStartupGuardTest (same package as the guard).
 * Runs without a database or full Spring Boot context.
 */
class CompetitionProfileContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CustomerServiceConfiguration.class);

    @Test
    void defaultsKeepEveryCustomerServiceFlagOff() {
        runner.run(context -> {
            CustomerServiceProperties properties = context.getBean(CustomerServiceProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getImport().isEnabled()).isFalse();
            assertThat(properties.getAssistance().isEnabled()).isFalse();
            assertThat(properties.getRisk().isEnabled()).isFalse();
            assertThat(properties.getAssistance().getMode())
                    .isEqualTo(CustomerServiceProperties.AssistanceMode.LIVE);
            assertThat(properties.getSourceRoot()).isEmpty();
        });
    }

    @Test
    void competitionValuesBindIncludingModeAndBudgets() {
        runner.withPropertyValues(
                "hmdp.customer-service.enabled=true",
                "hmdp.customer-service.source-root=C:/competition/data",
                "hmdp.customer-service.import.enabled=true",
                "hmdp.customer-service.import.staging-ttl-hours=12",
                "hmdp.customer-service.assistance.enabled=true",
                "hmdp.customer-service.assistance.mode=DETERMINISTIC_FALLBACK",
                "hmdp.customer-service.assistance.fallback-enabled=true",
                "hmdp.customer-service.assistance.completion-scan-delay-ms=2500",
                "hmdp.customer-service.assistance.model-timeout-seconds=20",
                "hmdp.customer-service.assistance.max-output-tokens=1600",
                "hmdp.customer-service.assistance.max-concurrent-runs=2",
                "hmdp.customer-service.risk.enabled=true",
                "hmdp.customer-service.risk.sla-scan-enabled=true"
        ).run(context -> {
            CustomerServiceProperties properties = context.getBean(CustomerServiceProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getSourceRoot()).isEqualTo("C:/competition/data");
            assertThat(properties.getImport().isEnabled()).isTrue();
            assertThat(properties.getImport().getStagingTtlHours()).isEqualTo(12);
            assertThat(properties.getAssistance().getMode())
                    .isEqualTo(CustomerServiceProperties.AssistanceMode.DETERMINISTIC_FALLBACK);
            assertThat(properties.getAssistance().getCompletionScanDelayMs()).isEqualTo(2500);
            assertThat(properties.getAssistance().getModelTimeoutSeconds()).isEqualTo(20);
            assertThat(properties.getAssistance().getMaxOutputTokens()).isEqualTo(1600);
            assertThat(properties.getAssistance().getMaxConcurrentRuns()).isEqualTo(2);
            assertThat(properties.getRisk().isEnabled()).isTrue();
        });
    }

    @Test
    void healthIndicatorReportsFlagsWithoutSecrets() {
        runner.withPropertyValues(
                "hmdp.customer-service.enabled=true",
                "hmdp.customer-service.assistance.enabled=true",
                "hmdp.customer-service.assistance.mode=LIVE"
        ).run(context -> {
            HealthIndicator indicator = context.getBean("customerServiceHealthIndicator",
                    HealthIndicator.class);
            Health health = indicator.health();
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails())
                    .containsEntry("enabled", true)
                    .containsEntry("assistanceMode", "LIVE")
                    .doesNotContainKeys("apiKey", "secret", "token");
        });
    }

    @Test
    void healthIndicatorIsOutOfServiceWhenDisabled() {
        runner.run(context -> {
            HealthIndicator indicator = context.getBean("customerServiceHealthIndicator",
                    HealthIndicator.class);
            assertThat(indicator.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        });
    }

    @Test
    void disabledFeatureGateReturnsStableErrorInsteadOf404() throws Exception {
        CustomerServiceFeatureGateFilter filter = new CustomerServiceFeatureGateFilter(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/customer-service/conversations");
        request.setRequestURI("/api/v1/customer-service/conversations");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("CS_FEATURE_DISABLED");
    }

    @Test
    void enabledFeatureGatePassesThrough() throws Exception {
        CustomerServiceFeatureGateFilter filter = new CustomerServiceFeatureGateFilter(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/customer-service/conversations");
        request.setRequestURI("/api/v1/customer-service/conversations");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void featureGateIgnoresNonCustomerServicePaths() throws Exception {
        CustomerServiceFeatureGateFilter filter = new CustomerServiceFeatureGateFilter(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/agents");
        request.setRequestURI("/api/v1/agents");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

}
