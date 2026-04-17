package com.srelab.tradeservice.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    /**
     * Add global common tags to ALL metrics.
     * These appear in every Prometheus/Grafana query automatically.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags(
                        "application", "trade-service",
                        "team", "sre-lab",
                        "domain", "trading"
                );
    }
}
