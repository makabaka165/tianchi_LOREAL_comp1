package com.hmdp.serviceassist.infrastructure.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers customer-service configuration and the health sub-component. The health
 * indicator is always present so operators can see whether the vertical is enabled and
 * in which generation mode it runs; it never exposes secrets. Model/agent-seed health
 * details are appended by later tasks once those components exist (AGENT-003+).
 */
@Configuration
@EnableConfigurationProperties(CustomerServiceProperties.class)
public class CustomerServiceConfiguration {

    @Bean
    public HealthIndicator customerServiceHealthIndicator(CustomerServiceProperties properties) {
        return () -> {
            Health.Builder builder = properties.isEnabled() ? Health.up() : Health.outOfService();
            return builder
                    .withDetail("enabled", properties.isEnabled())
                    .withDetail("importEnabled", properties.getImport().isEnabled())
                    .withDetail("assistanceEnabled", properties.getAssistance().isEnabled())
                    .withDetail("assistanceMode", properties.getAssistance().getMode().name())
                    .withDetail("fallbackEnabled", properties.getAssistance().isFallbackEnabled())
                    .withDetail("riskEnabled", properties.getRisk().isEnabled())
                    .build();
        };
    }
}
