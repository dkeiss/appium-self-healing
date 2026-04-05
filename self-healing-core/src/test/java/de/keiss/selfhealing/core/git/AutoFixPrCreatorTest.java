package de.keiss.selfhealing.core.git;

import de.keiss.selfhealing.core.model.HealingEvent;
import de.keiss.selfhealing.core.model.HealingResult;
import de.keiss.selfhealing.core.model.PullRequestEvent;
import de.keiss.selfhealing.core.model.TriageResult.FailureCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests AutoFixPrCreator filter logic, deduplication, and error handling. GitService and GitHubPrService are mocked —
 * no real Git or GitHub calls.
 */
class AutoFixPrCreatorTest {

    private GitService gitService;
    private GitHubPrService gitHubPrService;
    private RecordingEventPublisher eventPublisher;
    private AutoFixPrCreator creator;

    @BeforeEach
    void setUp() throws Exception {
        gitService = mock(GitService.class);
        gitHubPrService = mock(GitHubPrService.class);
        eventPublisher = new RecordingEventPublisher();
        creator = new AutoFixPrCreator(gitService, gitHubPrService, eventPublisher);

        when(gitService.commitAndPush(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("fix/self-healing-SearchPage-20260405-120000");
        when(gitHubPrService.createPullRequest(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn("https://github.com/test/repo/pull/42");
    }

    @Test
    void successfulLocatorHealing_createsPrAndPublishesEvent() throws Exception {
        var event = healingEvent(FailureCategory.LOCATOR_CHANGED, true, "fixed source", "SearchPage",
                "de/keiss/SearchPage.java");

        creator.onHealingEvent(event);

        verify(gitService).commitAndPush(eq("SearchPage"), eq("de/keiss/SearchPage.java"), eq("fixed source"),
                anyString());
        verify(gitHubPrService).createPullRequest(anyString(), eq("SearchPage"), anyString(), any(), anyString(),
                anyString());

        assertThat(eventPublisher.events).hasSize(1);
        assertThat(eventPublisher.events.get(0)).isInstanceOf(PullRequestEvent.class);
        var prEvent = (PullRequestEvent) eventPublisher.events.get(0);
        assertThat(prEvent.prUrl()).isEqualTo("https://github.com/test/repo/pull/42");
        assertThat(prEvent.pageObjectClassName()).isEqualTo("SearchPage");
    }

    @Test
    void failedHealing_isIgnored() {
        var event = healingEvent(FailureCategory.LOCATOR_CHANGED, false, null, "SearchPage",
                "de/keiss/SearchPage.java");

        creator.onHealingEvent(event);

        verifyNoInteractions(gitService, gitHubPrService);
        assertThat(eventPublisher.events).isEmpty();
    }

    @Test
    void nonLocatorCategory_isIgnored() {
        var event = healingEvent(FailureCategory.ENVIRONMENT_ISSUE, true, "fixed source", "SearchPage",
                "de/keiss/SearchPage.java");

        creator.onHealingEvent(event);

        verifyNoInteractions(gitService, gitHubPrService);
    }

    @Test
    void missingFixedSource_isIgnored() {
        var event = healingEvent(FailureCategory.LOCATOR_CHANGED, true, null, "SearchPage", "de/keiss/SearchPage.java");

        creator.onHealingEvent(event);

        verifyNoInteractions(gitService, gitHubPrService);
    }

    @Test
    void blankFixedSource_isIgnored() {
        var event = healingEvent(FailureCategory.LOCATOR_CHANGED, true, "   ", "SearchPage",
                "de/keiss/SearchPage.java");

        creator.onHealingEvent(event);

        verifyNoInteractions(gitService, gitHubPrService);
    }

    @Test
    void missingPageObjectMetadata_isIgnored() {
        var event = healingEvent(FailureCategory.LOCATOR_CHANGED, true, "fixed source", null, null);

        creator.onHealingEvent(event);

        verifyNoInteractions(gitService, gitHubPrService);
    }

    @Test
    void duplicatePageObject_isDeduplicatedWithinSession() throws Exception {
        var event1 = healingEvent(FailureCategory.LOCATOR_CHANGED, true, "fixed v1", "SearchPage",
                "de/keiss/SearchPage.java");
        var event2 = healingEvent(FailureCategory.LOCATOR_CHANGED, true, "fixed v2", "SearchPage",
                "de/keiss/SearchPage.java");

        creator.onHealingEvent(event1);
        creator.onHealingEvent(event2);

        // Only one PR should be created
        verify(gitService, times(1)).commitAndPush(anyString(), anyString(), anyString(), anyString());
        verify(gitHubPrService, times(1)).createPullRequest(anyString(), anyString(), anyString(), any(), anyString(),
                anyString());
    }

    @Test
    void differentPageObjects_eachGetOwnPr() throws Exception {
        var event1 = healingEvent(FailureCategory.LOCATOR_CHANGED, true, "fixed search", "SearchPage",
                "de/keiss/SearchPage.java");
        var event2 = healingEvent(FailureCategory.LOCATOR_CHANGED, true, "fixed result", "ResultPage",
                "de/keiss/ResultPage.java");

        creator.onHealingEvent(event1);
        creator.onHealingEvent(event2);

        verify(gitService, times(2)).commitAndPush(anyString(), anyString(), anyString(), anyString());
        assertThat(eventPublisher.events).hasSize(2);
    }

    @Test
    void gitFailure_doesNotCrashAndAllowsRetry() throws Exception {
        when(gitService.commitAndPush(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("network error")).thenReturn("fix/self-healing-SearchPage-retry");

        var event = healingEvent(FailureCategory.LOCATOR_CHANGED, true, "fixed source", "SearchPage",
                "de/keiss/SearchPage.java");

        // First attempt fails — should not throw
        creator.onHealingEvent(event);
        assertThat(eventPublisher.events).isEmpty();

        // Second attempt succeeds (className was removed from processed set)
        creator.onHealingEvent(event);
        assertThat(eventPublisher.events).hasSize(1);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private HealingEvent healingEvent(FailureCategory category, boolean success, String fixedSource, String className,
            String filePath) {
        var result = new HealingResult(success, null, // By healedLocator
                success ? "By.id(\"new_id\")" : null, fixedSource, "LLM explanation", 500, 200);
        return new HealingEvent(Instant.now(), "test scenario", category, result, "By.id(\"old_id\")", "anthropic",
                className, filePath);
    }

    /**
     * Simple event publisher that records all published events for assertions.
     */
    private static class RecordingEventPublisher implements ApplicationEventPublisher {

        final List<Object> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            events.add(event);
        }
    }
}
