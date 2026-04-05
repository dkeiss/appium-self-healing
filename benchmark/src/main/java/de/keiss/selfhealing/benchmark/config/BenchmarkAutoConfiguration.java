package de.keiss.selfhealing.benchmark.config;

import de.keiss.selfhealing.benchmark.collector.HealingMetricsCollector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers benchmark metric collection beans when {@code benchmark.enabled=true}.
 *
 * <p>
 * Mirrors the pattern used by {@code SelfHealingAutoConfiguration} in {@code self-healing-core} — plain
 * {@code @Configuration} class that is picked up whenever the benchmark module is on the (test) classpath. Activation
 * is opt-in via property, so standard test runs are unaffected.
 */
@Configuration
@EnableConfigurationProperties(BenchmarkProperties.class)
@ConditionalOnProperty(prefix = "benchmark", name = "enabled", havingValue = "true")
public class BenchmarkAutoConfiguration {

    @Bean
    public HealingMetricsCollector healingMetricsCollector(BenchmarkProperties properties) {
        return new HealingMetricsCollector(properties);
    }
}
