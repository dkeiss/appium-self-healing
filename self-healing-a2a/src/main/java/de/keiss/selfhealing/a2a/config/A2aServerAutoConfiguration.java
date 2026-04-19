package de.keiss.selfhealing.a2a.config;

import tools.jackson.databind.ObjectMapper;
import de.keiss.selfhealing.a2a.server.LocatorHealerA2AController;
import de.keiss.selfhealing.core.healing.ChatClientLocatorHealer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Activates the A2A server controller when {@code self-healing.a2a.server.enabled=true}. Kept separate from the client
 * autoconfig so a deployment can act only as server, only as client, or (in the spike) as both in the same JVM.
 */
@AutoConfiguration
@ConditionalOnClass(ChatClientLocatorHealer.class)
@ConditionalOnProperty(prefix = "self-healing.a2a.server", name = "enabled", havingValue = "true")
public class A2aServerAutoConfiguration {

    @Bean
    public LocatorHealerA2AController locatorHealerA2AController(ChatClientLocatorHealer healer,
            ObjectMapper objectMapper) {
        return new LocatorHealerA2AController(healer, objectMapper);
    }
}
