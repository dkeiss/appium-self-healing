package de.keiss.selfhealing.core.config;

import de.keiss.selfhealing.core.agent.TriageAgent;
import de.keiss.selfhealing.core.git.AutoFixPrCreator;
import de.keiss.selfhealing.core.git.GitHubPrService;
import de.keiss.selfhealing.core.git.GitService;
import de.keiss.selfhealing.core.healing.*;
import de.keiss.selfhealing.core.prompt.LocatorPromptCreator;
import de.keiss.selfhealing.core.prompt.StepPromptCreator;
import de.keiss.selfhealing.core.prompt.TriagePromptCreator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(SelfHealingProperties.class)
@ConditionalOnProperty(prefix = "self-healing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SelfHealingAutoConfiguration {

    @Bean
    public TriageAgent triageAgent(ChatClient.Builder chatClientBuilder) {
        return new TriageAgent(chatClientBuilder.build());
    }

    @Bean
    @Scope("prototype")
    public LocatorPromptCreator locatorPromptCreator() {
        return new LocatorPromptCreator();
    }

    @Bean
    @Scope("prototype")
    public TriagePromptCreator triagePromptCreator() {
        return new TriagePromptCreator();
    }

    @Bean
    @Scope("prototype")
    public StepPromptCreator stepPromptCreator() {
        return new StepPromptCreator();
    }

    @Bean
    public LocatorHealer locatorHealer(ChatClient.Builder chatClientBuilder, SelfHealingProperties properties) {
        return new LocatorHealer(chatClientBuilder.build(), properties);
    }

    @Bean
    public StepHealer stepHealer(ChatClient.Builder chatClientBuilder, SelfHealingProperties properties) {
        return new StepHealer(chatClientBuilder.build(), properties);
    }

    @Bean
    public PromptCache promptCache() {
        return new PromptCache();
    }

    @Bean
    @ConditionalOnProperty(prefix = "self-healing.environment-check", name = "enabled", havingValue = "true", matchIfMissing = true)
    public EnvironmentChecker environmentChecker(SelfHealingProperties properties) {
        return new EnvironmentChecker(properties.environmentCheck());
    }

    @Bean
    @ConditionalOnProperty(prefix = "self-healing.bug-reports", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AppBugReporter appBugReporter(SelfHealingProperties properties, ApplicationEventPublisher eventPublisher) {
        return new AppBugReporter(properties.bugReports(), eventPublisher);
    }

    // ── Auto-Fix PR beans (opt-in via self-healing.git-pr.enabled=true) ─────

    @Bean
    @ConditionalOnProperty(prefix = "self-healing.git-pr", name = "enabled", havingValue = "true")
    public GitService gitService(SelfHealingProperties properties) {
        String sourceBase = properties.sourceBasePath() != null ? properties.sourceBasePath()
                : System.getProperty("user.dir");
        return new GitService(Path.of(sourceBase), properties.gitPr());
    }

    @Bean
    @ConditionalOnProperty(prefix = "self-healing.git-pr", name = "enabled", havingValue = "true")
    public GitHubPrService gitHubPrService(SelfHealingProperties properties) {
        return new GitHubPrService(properties.gitPr());
    }

    @Bean
    @ConditionalOnProperty(prefix = "self-healing.git-pr", name = "enabled", havingValue = "true")
    public AutoFixPrCreator autoFixPrCreator(GitService gitService, GitHubPrService gitHubPrService,
            ApplicationEventPublisher eventPublisher) {
        return new AutoFixPrCreator(gitService, gitHubPrService, eventPublisher);
    }

    // ── Healing orchestrator ────────────────────────────────────────────────

    @Bean
    public HealingOrchestrator healingOrchestrator(TriageAgent triageAgent, LocatorHealer locatorHealer,
            StepHealer stepHealer, PromptCache promptCache, SelfHealingProperties properties,
            ApplicationEventPublisher eventPublisher,
            org.springframework.beans.factory.ObjectProvider<EnvironmentChecker> environmentCheckerProvider,
            org.springframework.beans.factory.ObjectProvider<AppBugReporter> bugReporterProvider) {
        return new HealingOrchestrator(triageAgent, locatorHealer, stepHealer, promptCache,
                environmentCheckerProvider.getIfAvailable(), bugReporterProvider.getIfAvailable(), properties,
                eventPublisher);
    }
}
