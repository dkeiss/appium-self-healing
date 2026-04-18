package de.keiss.selfhealing.core.healing;

import de.keiss.selfhealing.core.agent.TriageAgent;
import de.keiss.selfhealing.core.config.SelfHealingProperties;
import de.keiss.selfhealing.core.config.SelfHealingProperties.BugReports;
import de.keiss.selfhealing.core.config.SelfHealingProperties.Cache;
import de.keiss.selfhealing.core.config.SelfHealingProperties.EnvironmentCheck;
import de.keiss.selfhealing.core.config.SelfHealingProperties.Mcp;
import de.keiss.selfhealing.core.config.SelfHealingProperties.Triage;
import de.keiss.selfhealing.core.config.SelfHealingProperties.Vision;
import de.keiss.selfhealing.core.model.FailureContext;
import de.keiss.selfhealing.core.model.HealingResult;
import de.keiss.selfhealing.core.model.TriageResult;
import de.keiss.selfhealing.core.model.TriageResult.FailureCategory;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HealingOrchestrator} focusing on the {@code self-healing.cache.enabled} gate.
 *
 * Context: In LLM benchmark runs, a single wrong heal in scenario 1 would otherwise be cached and reused in every
 * subsequent scenario — masking the LLM's ability to heal the remaining locators. Setting {@code cache.enabled=false}
 * forces the orchestrator to invoke the LocatorHealer fresh for every failure, yielding a comparable per-scenario heal
 * quality measurement.
 */
class HealingOrchestratorTest {

    @Test
    void cacheEnabled_reusesCachedHeal_andSkipsSecondLocatorHealerInvocation() {
        var properties = propertiesWithCache(true);
        var triage = new StubTriageAgent(FailureCategory.LOCATOR_CHANGED, "locator changed", 0.9);
        var healer = new StubLocatorHealer(HealingResult.failed("wrong heal", 10));
        // Mark success so the result is actually cached (PromptCache only stores successful heals)
        healer.nextResult = new HealingResult(true, By.id("healed"), "id(\"healed\")", null, "stub heal", 10, 0);
        var cache = new PromptCache();
        var publisher = new RecordingEventPublisher();

        var orchestrator = new HealingOrchestrator(triage, healer, null, cache, null, null, null, properties,
                publisher);

        var context = failureContextForLocator(By.id("search_button"));

        HealingResult first = orchestrator.attemptHealing(context);
        HealingResult second = orchestrator.attemptHealing(context);

        assertThat(first.success()).isTrue();
        assertThat(second.success()).isTrue();
        assertThat(healer.callCount).as("second call must be served from cache, not re-invoke the LLM healer").isEqualTo(1);
        assertThat(cache.size()).as("successful heal is persisted in cache").isEqualTo(1);
        assertThat(cache.getHits()).isEqualTo(1);
        assertThat(publisher.events).as("cache hit short-circuits before publishing a new event").hasSize(1);
    }

    @Test
    void cacheDisabled_bypassesCacheEntirely_invokingHealerOnEveryCall() {
        var properties = propertiesWithCache(false);
        var triage = new StubTriageAgent(FailureCategory.LOCATOR_CHANGED, "locator changed", 0.9);
        var healer = new StubLocatorHealer(
                new HealingResult(true, By.id("healed"), "id(\"healed\")", null, "stub heal", 10, 0));
        var cache = new PromptCache();
        var publisher = new RecordingEventPublisher();

        var orchestrator = new HealingOrchestrator(triage, healer, null, cache, null, null, null, properties,
                publisher);

        var context = failureContextForLocator(By.id("search_button"));

        HealingResult first = orchestrator.attemptHealing(context);
        HealingResult second = orchestrator.attemptHealing(context);

        assertThat(first.success()).isTrue();
        assertThat(second.success()).isTrue();
        assertThat(healer.callCount).as("each attempt must re-invoke the LLM healer when cache is disabled")
                .isEqualTo(2);
        assertThat(cache.size()).as("cache.put must not be called when cache is disabled").isZero();
        assertThat(cache.getHits()).as("cache.get must not be called when cache is disabled").isZero();
        assertThat(cache.getMisses()).as("cache.get must not be called when cache is disabled").isZero();
        assertThat(publisher.events).as("every heal publishes an event when cache is bypassed").hasSize(2);
    }

    @Test
    void cacheDisabled_doesNotPoisonSubsequentHealsWhenFirstHealIsWrong() {
        // Reproduces the GLM-4.7-Flash benchmark regression: a wrong heal for locator A must not affect
        // the orchestrator's willingness to heal locator B (different cache key) in a later scenario.
        // With cache disabled, the second heal is fully independent of the first — even if both locators
        // hashed to the same key, no stale entry would be served.
        var properties = propertiesWithCache(false);
        var triage = new StubTriageAgent(FailureCategory.LOCATOR_CHANGED, "locator changed", 0.9);
        var healer = new ScriptedLocatorHealer(
                // scenario 1 — LLM hallucinates a wrong-but-successful heal
                new HealingResult(true, By.id("label_instead_of_input"), "id(\"label_instead_of_input\")", null,
                        "wrong label", 10, 0),
                // scenario 2 — different locator, LLM produces the correct heal
                new HealingResult(true, By.id("btn_search"), "id(\"btn_search\")", null, "correct", 10, 0));
        var cache = new PromptCache();
        var publisher = new RecordingEventPublisher();

        var orchestrator = new HealingOrchestrator(triage, healer, null, cache, null, null, null, properties,
                publisher);

        HealingResult scenario1 = orchestrator.attemptHealing(failureContextForLocator(By.id("input_to")));
        HealingResult scenario2 = orchestrator.attemptHealing(failureContextForLocator(By.id("btn_search")));

        assertThat(scenario1.healedLocatorExpression()).isEqualTo("id(\"label_instead_of_input\")");
        assertThat(scenario2.healedLocatorExpression())
                .as("scenario 2 must get its own fresh heal, not be poisoned by scenario 1's bad cache entry")
                .isEqualTo("id(\"btn_search\")");
        assertThat(healer.callCount).isEqualTo(2);
        assertThat(cache.size()).isZero();
    }

    // --- Helpers ---------------------------------------------------------------------

    private static SelfHealingProperties propertiesWithCache(boolean cacheEnabled) {
        return new SelfHealingProperties(true, 3, "anthropic", null, new Triage(true), new Mcp(false),
                new Vision(false), new Cache(cacheEnabled), EnvironmentCheck.defaults(), BugReports.defaults(), null);
    }

    private static FailureContext failureContextForLocator(By locator) {
        return new FailureContext("io.appium.java_client.NoSuchElementException", "<hierarchy/>", new byte[0], locator,
                null, "SearchPage", null, "scenario step");
    }

    // --- Test doubles ----------------------------------------------------------------

    private static final class StubTriageAgent extends TriageAgent {

        private final TriageResult fixed;

        StubTriageAgent(FailureCategory category, String reasoning, double confidence) {
            super(null);
            this.fixed = new TriageResult(category, reasoning, confidence);
        }

        @Override
        public TriageResult analyze(FailureContext context) {
            return fixed;
        }
    }

    private static class StubLocatorHealer extends LocatorHealer {

        HealingResult nextResult;
        int callCount = 0;

        StubLocatorHealer(HealingResult result) {
            super(null, null);
            this.nextResult = result;
        }

        @Override
        public HealingResult heal(FailureContext context) {
            callCount++;
            return nextResult;
        }
    }

    private static final class ScriptedLocatorHealer extends LocatorHealer {

        private final List<HealingResult> scripted;
        int callCount = 0;

        ScriptedLocatorHealer(HealingResult... results) {
            super(null, null);
            this.scripted = List.of(results);
        }

        @Override
        public HealingResult heal(FailureContext context) {
            HealingResult result = scripted.get(callCount);
            callCount++;
            return result;
        }
    }

    private static final class RecordingEventPublisher implements ApplicationEventPublisher {

        final List<Object> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            events.add(event);
        }
    }
}
