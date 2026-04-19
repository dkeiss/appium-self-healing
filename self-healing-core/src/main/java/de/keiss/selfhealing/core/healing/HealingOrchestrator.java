package de.keiss.selfhealing.core.healing;

import de.keiss.selfhealing.core.agent.TriageAgent;
import de.keiss.selfhealing.core.config.SelfHealingProperties;
import de.keiss.selfhealing.core.model.*;
import de.keiss.selfhealing.core.model.TriageResult.FailureCategory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;

/**
 * Orchestrates the three-stage healing pipeline:
 *
 * Stage 1: Triage — classify the failure Stage 2: Handler — delegate to specialist (LocatorHealer, StepHealer,
 * EnvironmentChecker, AppBugReporter) Stage 3: Verification — publish event and optional MCP enrichment
 *
 * Uses PromptCache to avoid repeated LLM calls for the same broken locator.
 */
@Slf4j
@RequiredArgsConstructor
public class HealingOrchestrator {

    private final TriageAgent triageAgent;
    private final LocatorHealer locatorHealer;
    private final StepHealer stepHealer;
    @Getter
    private final PromptCache promptCache;
    private final McpContextEnricher mcpEnricher;
    private final EnvironmentChecker environmentChecker;
    private final AppBugReporter bugReporter;
    private final SelfHealingProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public HealingResult attemptHealing(FailureContext context) {
        boolean cacheEnabled = properties.cache().enabled();
        String cacheKey = buildCacheKey(context);

        // A non-empty rejectedLocators list means this is a driver-level retry after a previously-healed locator
        // failed to resolve on the page. In that case, a cache hit would just return the same bad answer, so we
        // bypass the cache AND invalidate the entry — otherwise the bad suggestion poisons every later scenario
        // that hits the same broken locator (observed with Mistral hallucinating `leg_item_0_platform` for v2
        // BottomSheet scenario, where all 3 retries kept returning the cached non-existent id).
        boolean isRetry = context.rejectedLocators() != null && !context.rejectedLocators().isEmpty();

        // Check cache first — skipped entirely when disabled so benchmark runs can
        // compare the LLM's heal quality per scenario without a bad early heal
        // poisoning every subsequent scenario via cache hit.
        if (cacheEnabled && !isRetry) {
            HealingResult cached = promptCache.get(cacheKey);
            if (cached != null) {
                log.info("Using cached healing for: {}", cacheKey);
                return cached;
            }
        } else if (isRetry && cacheEnabled) {
            log.info("Retry with {} rejected locator(s) — bypassing cache and invalidating stale entry for: {}",
                    context.rejectedLocators().size(), cacheKey);
            promptCache.invalidate(cacheKey);
        } else {
            log.debug("Cache disabled — skipping lookup for: {}", cacheKey);
        }

        // Stage 0 (optional): MCP enrichment
        FailureContext enrichedContext = enrichContext(context);

        // Stage 1: Triage
        TriageResult triage = performTriage(enrichedContext);

        // Stage 2: Delegate to specialized handler
        HealingResult result = delegateToHandler(triage, enrichedContext);

        // Stage 3: Post-processing
        if (cacheEnabled && result.success()) {
            promptCache.put(cacheKey, result);
        }
        publishHealingEvent(enrichedContext, triage, result);

        return result;
    }

    private FailureContext enrichContext(FailureContext context) {
        if (mcpEnricher != null && properties.mcp() != null && properties.mcp().enabled()) {
            return mcpEnricher.enrich(context);
        }
        return context;
    }

    private TriageResult performTriage(FailureContext context) {
        if (properties.triage().enabled()) {
            return triageAgent.analyze(context);
        }
        // Skip triage — assume locator change (fastest path for demos)
        return new TriageResult(FailureCategory.LOCATOR_CHANGED, "Triage disabled, assuming locator change", 1.0);
    }

    private HealingResult delegateToHandler(TriageResult triage, FailureContext context) {
        return switch (triage.category()) {
            case LOCATOR_CHANGED -> {
                log.info("Delegating to LocatorHealer...");
                yield locatorHealer.heal(context);
            }
            case TEST_LOGIC_ERROR -> {
                log.info("Delegating to StepHealer...");
                if (stepHealer != null) {
                    var stepResult = stepHealer.heal(context);
                    yield stepHealer.toHealingResult(stepResult);
                }
                log.warn("StepHealer not available. Manual fix required: {}", triage.reasoning());
                yield HealingResult.failed("Test logic error — manual fix required: " + triage.reasoning(), 0);
            }
            case ENVIRONMENT_ISSUE -> handleEnvironmentIssue(triage, context);
            case APP_BUG -> handleAppBug(triage, context);
        };
    }

    private HealingResult handleEnvironmentIssue(TriageResult triage, FailureContext context) {
        log.warn("Environment issue detected: {}", triage.reasoning());
        if (environmentChecker == null) {
            return HealingResult.failed("Environment issue — check infrastructure: " + triage.reasoning(), 0);
        }

        long start = System.currentTimeMillis();
        EnvironmentReport report = environmentChecker.check(context);
        long duration = System.currentTimeMillis() - start;

        String diagnostic = "Environment issue — " + report.summary();
        if (report.recoverable()) {
            // Transient issue — orchestrator caller may retry. Signal failure but
            // with actionable diagnostic; actual retry loop lives in SelfHealingAppiumDriver.
            log.info("Environment appears recoverable — caller may retry after backoff");
            environmentChecker.waitBeforeRetry();
            return HealingResult.failed(diagnostic + " (transient — retry recommended)", duration);
        }
        return HealingResult.failed(diagnostic + " (terminal — abort)", duration);
    }

    private HealingResult handleAppBug(TriageResult triage, FailureContext context) {
        log.warn("Application bug detected: {}", triage.reasoning());
        if (bugReporter == null) {
            return HealingResult.failed("Application bug — not a test issue: " + triage.reasoning(), 0);
        }

        long start = System.currentTimeMillis();
        BugReport report = bugReporter.report(context, triage, properties.llmProvider());
        long duration = System.currentTimeMillis() - start;

        String explanation = "App bug reported [" + report.id() + "] " + report.severity() + ": " + report.title();
        return HealingResult.failed(explanation, duration);
    }

    private void publishHealingEvent(FailureContext context, TriageResult triage, HealingResult result) {
        var event = new HealingEvent(Instant.now(), context.stepName(), triage.category(), result,
                context.failedLocator() != null ? context.failedLocator().toString() : "unknown",
                properties.llmProvider(), context.pageObjectClassName(), resolvePageObjectFilePath(context));
        eventPublisher.publishEvent(event);
        log.info("Healing event published for: {} [{}] success={}", context.stepName(), triage.category(),
                result.success());
    }

    /**
     * Derives the relative file path for the page object source. The SourceCodeResolver uses the full class name to
     * locate the file on disk; we reconstruct the same relative path here so that the PR creator can write the fixed
     * source back to the correct location.
     */
    private String resolvePageObjectFilePath(FailureContext context) {
        if (context.pageObjectClassName() == null || context.pageObjectSource() == null) {
            return null;
        }
        // pageObjectClassName is the simple name (e.g. "SearchPage"). We need
        // the full path, which we infer from the source content's package declaration.
        String source = context.pageObjectSource();
        String pkg = extractPackage(source);
        if (pkg != null) {
            return pkg.replace('.', '/') + "/" + context.pageObjectClassName() + ".java";
        }
        return context.pageObjectClassName() + ".java";
    }

    private String extractPackage(String source) {
        for (String line : source.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("package ") && trimmed.endsWith(";")) {
                return trimmed.substring("package ".length(), trimmed.length() - 1).trim();
            }
        }
        return null;
    }

    private String buildCacheKey(FailureContext context) {
        return context.failedLocator() != null ? context.failedLocator().toString() : context.exceptionMessage();
    }
}
