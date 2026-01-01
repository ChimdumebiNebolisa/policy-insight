package com.policyinsight.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.statsd.StatsdConfig;
import io.micrometer.statsd.StatsdMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for Datadog metrics via StatsD/DogStatsD.
 * Only active when datadog.enabled=true.
 * Adds StatsD registry to the composite registry so metrics are sent to both
 * the default registry (for actuator endpoints) and Datadog.
 *
 * References:
 * - https://docs.datadoghq.com/developers/dogstatsd/?tab=java
 * - https://micrometer.io/docs/registry/statsd
 */
@Configuration
@ConditionalOnProperty(name = "datadog.enabled", havingValue = "true", matchIfMissing = false)
public class DatadogMetricsConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatadogMetricsConfig.class);

    @Value("${DD_AGENT_HOST:localhost}")
    private String agentHost;

    @Value("${DD_DOGSTATSD_PORT:8125}")
    private int statsdPort;

    @Bean
    public StatsdConfig statsdConfig() {
        return new StatsdConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String host() {
                return agentHost;
            }

            @Override
            public int port() {
                return statsdPort;
            }

            @Override
            public String prefix() {
                return "policyinsight";
            }

            @Override
            public boolean enabled() {
                return true;
            }
        };
    }

    @Bean
    public StatsdMeterRegistry statsdMeterRegistry(StatsdConfig statsdConfig) {
        StatsdMeterRegistry registry = new StatsdMeterRegistry(statsdConfig, io.micrometer.core.instrument.Clock.SYSTEM);
        logger.info("Datadog StatsD meter registry configured: host={}, port={}", agentHost, statsdPort);
        return registry;
    }

    /**
     * Create a composite registry that includes both the default registry and StatsD.
     * This ensures metrics are available via actuator endpoints AND sent to Datadog.
     */
    @Bean
    @Primary
    public CompositeMeterRegistry compositeMeterRegistry(MeterRegistry defaultRegistry, StatsdMeterRegistry statsdRegistry) {
        CompositeMeterRegistry composite = new CompositeMeterRegistry();
        composite.add(defaultRegistry);
        composite.add(statsdRegistry);
        logger.info("Composite meter registry created with default and StatsD registries");
        return composite;
    }
}

