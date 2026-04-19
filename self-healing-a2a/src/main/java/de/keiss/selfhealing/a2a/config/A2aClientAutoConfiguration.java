package de.keiss.selfhealing.a2a.config;

import tools.jackson.databind.ObjectMapper;
import de.keiss.selfhealing.a2a.client.A2AClient;
import de.keiss.selfhealing.a2a.client.A2ALocatorHealer;
import de.keiss.selfhealing.core.config.SelfHealingProperties;
import de.keiss.selfhealing.core.healing.LocatorHealer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * Registers an {@link A2ALocatorHealer} as {@link Primary} {@link LocatorHealer} when
 * {@code self-healing.a2a.client.enabled=true}. The core autoconfig declares its own {@code LocatorHealer} bean with
 * {@link ConditionalOnMissingBean}, so the remote proxy fully replaces it.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "self-healing.a2a.client", name = "enabled", havingValue = "true")
public class A2aClientAutoConfiguration {

    @Bean
    public A2AClient a2aClient(SelfHealingProperties properties, ObjectMapper objectMapper) {
        var clientCfg = properties.a2a().client();
        String url = clientCfg.locatorHealerUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("self-healing.a2a.client.enabled=true but "
                    + "self-healing.a2a.client.locator-healer-url is not set");
        }
        return new A2AClient(url, objectMapper, Duration.ofMillis(clientCfg.requestTimeoutMs()));
    }

    @Bean
    @Primary
    public LocatorHealer a2aLocatorHealer(A2AClient a2aClient) {
        return new A2ALocatorHealer(a2aClient);
    }
}
