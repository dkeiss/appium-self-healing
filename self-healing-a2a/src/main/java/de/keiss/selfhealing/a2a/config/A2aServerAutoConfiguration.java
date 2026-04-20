package de.keiss.selfhealing.a2a.config;

import tools.jackson.databind.ObjectMapper;
import de.keiss.selfhealing.a2a.server.SelfHealingA2AController;
import de.keiss.selfhealing.core.agent.ChatClientTriageAgent;
import de.keiss.selfhealing.core.healing.ChatClientLocatorHealer;
import de.keiss.selfhealing.core.healing.ChatClientMcpContextEnricher;
import de.keiss.selfhealing.core.healing.ChatClientStepHealer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Activates the A2A server controller when {@code self-healing.a2a.server.enabled=true}.
 * Kept separate from the client autoconfig so a deployment can act only as server, only as
 * client, or (in the spike) as both in the same JVM.
 */
@AutoConfiguration
@ConditionalOnClass(ChatClientLocatorHealer.class)
@ConditionalOnProperty(prefix = "self-healing.a2a.server", name = "enabled", havingValue = "true")
public class A2aServerAutoConfiguration {

    @Bean
    public SelfHealingA2AController selfHealingA2AController(ChatClientLocatorHealer locatorHealer,
            ChatClientTriageAgent triageAgent, ChatClientStepHealer stepHealer,
            ObjectProvider<ChatClientMcpContextEnricher> mcpEnricherProvider, ObjectMapper objectMapper) {
        return new SelfHealingA2AController(locatorHealer, triageAgent, stepHealer,
                mcpEnricherProvider.getIfAvailable(), objectMapper);
    }
}
