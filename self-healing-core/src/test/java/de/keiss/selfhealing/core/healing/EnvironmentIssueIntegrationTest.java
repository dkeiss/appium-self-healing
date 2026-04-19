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
import de.keiss.selfhealing.core.model.HealingEvent;
import de.keiss.selfhealing.core.model.HealingResult;
import de.keiss.selfhealing.core.model.TriageResult;
import de.keiss.selfhealing.core.model.TriageResult.FailureCategory;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.springframework.context.ApplicationEventPublisher;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the {@code ENVIRONMENT_ISSUE} pipeline in {@link HealingOrchestrator}.
 *
 * Verifies that when triage classifies a failure as an environment issue:
 * <ol>
 * <li>The orchestrator delegates to {@link EnvironmentChecker} instead of invoking the LLM locator healer.</li>
 * <li>The {@link HealingResult} is a failure carrying a diagnostic message.</li>
 * <li>The message distinguishes terminal ("abort") from transient ("retry recommended").</li>
 * <li>A {@link HealingEvent} is published regardless of success.</li>
 * </ol>
 *
 * The {@link TriageAgent} is stubbed to always return {@code ENVIRONMENT_ISSUE} so the test focuses purely on
 * orchestrator + EnvironmentChecker wiring — no live LLM required.
 *
 * Two scenarios are covered:
 * <ul>
 * <li><b>Terminal path</b>: checker points at a guaranteed-unreachable URL ({@code http://127.0.0.1:1}) so the backend
 * probe fails → abort with diagnostic.</li>
 * <li><b>Transient path</b>: a tiny in-process {@link HttpServer} acts as a healthy backend, so the environment check
 * reports recoverable → orchestrator waits for backoff and signals retry.</li>
 * </ul>
 */
class EnvironmentIssueIntegrationTest {

    @Test
    void unreachableBackend_triggersTerminalDiagnostic_withoutCallingLocatorHealer() {
        // --- Arrange ---
        var properties = new SelfHealingProperties(true, 3, "anthropic", null, new Triage(true), new Mcp(false),
                new Vision(false), Cache.defaults(), new EnvironmentCheck(true, "http://127.0.0.1:1/health", // unreachable
                                                                                                             // — OS
                                                                                                             // rejects
                                                                                                             // connection
                                                                                                             // instantly
                        null, // no Appium probe in this test
                        500, // connect timeout — fail fast
                        500, // request timeout
                        10 // retry backoff (not used on terminal path, kept small for safety)
                ), BugReports.defaults(), null, // gitPr — not relevant for environment tests
                null // a2a — not relevant for environment tests
        );

        var triageAgent = new StubTriageAgent(FailureCategory.ENVIRONMENT_ISSUE, "Backend health check failed", 0.95);
        var environmentChecker = new EnvironmentChecker(properties.environmentCheck());
        var recordingPublisher = new RecordingEventPublisher();

        var orchestrator = new HealingOrchestrator(triageAgent, null, // LocatorHealer — must not be invoked on this
                                                                      // path
                null, // StepHealer — must not be invoked on this path
                new PromptCache(), null, // McpContextEnricher disabled
                environmentChecker, null, // AppBugReporter — must not be invoked on this path
                properties, recordingPublisher);

        var failureContext = new FailureContext("io.appium.java_client.NoSuchElementException: no such element",
                "<hierarchy/>", // non-empty page source — rules out the emulator-dead heuristic
                new byte[0], By.id("search_button"), null, "SearchPage", null, "User taps search");

        // --- Act ---
        HealingResult result = orchestrator.attemptHealing(failureContext);

        // --- Assert ---
        assertThat(result.success()).as("environment issues are never successful heals").isFalse();

        assertThat(result.explanation()).as("diagnostic should identify the issue and the downed service")
                .contains("Environment issue").contains("backend=DOWN").contains("terminal"); // backend unreachable →
                                                                                              // not recoverable

        assertThat(result.healedLocator()).as("no locator healing should have occurred").isNull();

        assertThat(result.tokensUsed())
                .as("no LLM healing call should have been made — environment path is free of token usage").isZero();

        assertThat(recordingPublisher.events)
                .as("a HealingEvent must be published even on failure so reporters stay in sync").hasSize(1).first()
                .isInstanceOfSatisfying(HealingEvent.class, event -> {
                    assertThat(event.category()).isEqualTo(FailureCategory.ENVIRONMENT_ISSUE);
                    assertThat(event.healingResult().success()).isFalse();
                    assertThat(event.scenarioName()).isEqualTo("User taps search");
                });
    }

    @Test
    void reachableBackend_triggersTransientDiagnostic_andWaitsBeforeRetry() throws Exception {
        // --- Arrange: spin up a tiny healthy backend on a random free port ---
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            byte[] body = "OK".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        int port = server.getAddress().getPort();

        try {
            var properties = new SelfHealingProperties(true, 3, "anthropic", null, new Triage(true), new Mcp(false),
                    new Vision(false), Cache.defaults(),
                    new EnvironmentCheck(true, "http://127.0.0.1:" + port + "/health", // reachable
                            null, 500, 500, 50 // retry backoff — short but observable via spy
                    ), BugReports.defaults(), null, // gitPr — not relevant for environment tests
                    null // a2a — not relevant for environment tests
            );

            var triageAgent = new StubTriageAgent(FailureCategory.ENVIRONMENT_ISSUE, "Network flakiness suspected",
                    0.80);
            var checker = new SpyEnvironmentChecker(properties.environmentCheck());
            var recordingPublisher = new RecordingEventPublisher();

            var orchestrator = new HealingOrchestrator(triageAgent, null, null, new PromptCache(), null, checker, null,
                    properties, recordingPublisher);

            var failureContext = new FailureContext("org.openqa.selenium.TimeoutException: timed out after 10 seconds",
                    "<hierarchy><node class=\"android.widget.FrameLayout\"/></hierarchy>", // non-empty
                    new byte[0], By.id("search_button"), null, "SearchPage", null, "User taps search");

            // --- Act ---
            HealingResult result = orchestrator.attemptHealing(failureContext);

            // --- Assert ---
            assertThat(result.success()).as("still a failure — orchestrator only signals retry, caller decides")
                    .isFalse();

            assertThat(result.explanation()).as("diagnostic should flag transient nature and recommend retry")
                    .contains("Environment issue").contains("backend=OK").contains("transient")
                    .contains("retry recommended");

            assertThat(checker.waitCalled).as("orchestrator must invoke waitBeforeRetry() on the recoverable path")
                    .isTrue();

            assertThat(result.tokensUsed()).as("recoverable env issue must not spend LLM tokens on locator healing")
                    .isZero();

            assertThat(recordingPublisher.events).as("a HealingEvent is published in the transient path too").hasSize(1)
                    .first().isInstanceOfSatisfying(HealingEvent.class,
                            event -> assertThat(event.category()).isEqualTo(FailureCategory.ENVIRONMENT_ISSUE));
        } finally {
            server.stop(0);
        }
    }

    // --- Test doubles ----------------------------------------------------------------

    /**
     * Stub TriageAgent that bypasses the LLM entirely. The superclass ChatClient is passed {@code null} — it is never
     * dereferenced because {@link #analyze(FailureContext)} is overridden.
     */
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

    /**
     * Minimal {@link ApplicationEventPublisher} that records all published events so the test can assert on them.
     */
    private static final class RecordingEventPublisher implements ApplicationEventPublisher {

        final List<Object> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            events.add(event);
        }
    }

    /**
     * Spy that tracks whether {@link #waitBeforeRetry()} was called. Used to verify the transient/recoverable path
     * without relying on wall-clock timing assertions (which are flaky on slow CI).
     */
    private static final class SpyEnvironmentChecker extends EnvironmentChecker {

        boolean waitCalled = false;

        SpyEnvironmentChecker(SelfHealingProperties.EnvironmentCheck config) {
            super(config);
        }

        @Override
        public void waitBeforeRetry() {
            waitCalled = true;
            super.waitBeforeRetry();
        }
    }
}
