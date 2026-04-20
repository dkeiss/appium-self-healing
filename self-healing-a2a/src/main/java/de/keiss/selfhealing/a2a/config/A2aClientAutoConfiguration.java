package de.keiss.selfhealing.a2a.config;

import tools.jackson.databind.ObjectMapper;
import de.keiss.selfhealing.a2a.client.*;
import de.keiss.selfhealing.core.agent.TriageAgent;
import de.keiss.selfhealing.core.config.SelfHealingProperties;
import de.keiss.selfhealing.core.healing.LocatorHealer;
import de.keiss.selfhealing.core.healing.McpContextEnricher;
import de.keiss.selfhealing.core.healing.StepHealer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * Registers A2A proxy beans as {@link Primary} when {@code self-healing.a2a.client.enabled=true}.
 * The core autoconfig declares each bean with {@link ConditionalOnMissingBean}, so the remote
 * proxies fully replace in-process implementations.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "self-healing.a2a.client", name = "enabled", havingValue = "true")
public class A2aClientAutoConfiguration {

    @Bean
    public A2AClient a2aClient(SelfHealingProperties properties, ObjectMapper objectMapper) {
        var clientCfg = properties.a2a().client();
        String url = clientCfg.serverUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("self-healing.a2a.client.enabled=true but "
                    + "self-healing.a2a.client.server-url is not set");
        }
        return new A2AClient(url, objectMapper, Duration.ofMillis(clientCfg.requestTimeoutMs()));
    }

    @Bean
    @Primary
    public LocatorHealer a2aLocatorHealer(A2AClient a2aClient) {
        return new A2ALocatorHealer(a2aClient);
    }

    @Bean
    @Primary
    public TriageAgent a2aTriageAgent(A2AClient a2aClient) {
        return new A2ATriageAgent(a2aClient);
    }

    @Bean
    @Primary
    public StepHealer a2aStepHealer(A2AClient a2aClient) {
        return new A2AStepHealer(a2aClient);
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(McpContextEnricher.class)
    public McpContextEnricher a2aMcpContextEnricher(A2AClient a2aClient) {
        return new A2AMcpContextEnricher(a2aClient);
    }
}
