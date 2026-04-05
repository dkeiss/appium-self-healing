package de.keiss.selfhealing.core.git;

import de.keiss.selfhealing.core.model.HealingEvent;
import de.keiss.selfhealing.core.model.PullRequestEvent;
import de.keiss.selfhealing.core.model.TriageResult.FailureCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Listens for successful healing events and creates auto-fix PRs on GitHub.
 *
 * <p>
 * Only reacts to {@link FailureCategory#LOCATOR_CHANGED} events where the healer produced a
 * {@code fixedPageObjectSource}. Duplicate PRs for the same page object within a single session are suppressed via a
 * className set.
 *
 * <p>
 * PR creation never blocks or crashes the test run — all errors are caught and logged.
 */
@Slf4j
@RequiredArgsConstructor
public class AutoFixPrCreator {

    private final GitService gitService;
    private final GitHubPrService gitHubPrService;
    private final ApplicationEventPublisher eventPublisher;

    /** Tracks page objects for which a PR has already been created in this session. */
    private final Set<String> processedPageObjects = new CopyOnWriteArraySet<>();

    @EventListener
    public void onHealingEvent(HealingEvent event) {
        if (!isEligibleForPr(event)) {
            return;
        }

        String className = event.pageObjectClassName();
        if (!processedPageObjects.add(className)) {
            log.debug("PR already created for {} in this session — skipping", className);
            return;
        }

        try {
            createPr(event);
        } catch (Exception e) {
            // Never let PR creation failures break the test run
            log.error("Failed to create auto-fix PR for {}: {}", className, e.getMessage(), e);
            processedPageObjects.remove(className); // Allow retry on next event
        }
    }

    boolean isEligibleForPr(HealingEvent event) {
        if (event.category() != FailureCategory.LOCATOR_CHANGED) {
            return false;
        }
        if (!event.healingResult().success()) {
            return false;
        }
        if (event.healingResult().fixedPageObjectSource() == null
                || event.healingResult().fixedPageObjectSource().isBlank()) {
            return false;
        }
        if (event.pageObjectClassName() == null || event.pageObjectFilePath() == null) {
            log.warn("HealingEvent missing page object metadata — cannot create PR");
            return false;
        }
        return true;
    }

    private void createPr(HealingEvent event) throws Exception {
        String className = event.pageObjectClassName();
        String filePath = event.pageObjectFilePath();
        String fixedSource = event.healingResult().fixedPageObjectSource();

        String commitMessage = "fix(self-healing): update " + className + " locators\n\n" + "Healed by "
                + event.llmProvider() + " during scenario: " + event.scenarioName() + "\n" + "Original locator: "
                + event.originalLocator() + "\n" + "Healed locator: " + event.healingResult().healedLocatorExpression();

        // 1. Commit and push fix branch
        String branchName = gitService.commitAndPush(className, filePath, fixedSource, commitMessage);

        // 2. Create PR on GitHub
        String prUrl = gitHubPrService.createPullRequest(branchName, className, event.originalLocator(),
                event.healingResult(), event.scenarioName(), event.llmProvider());

        // 3. Publish PR event for downstream listeners
        var prEvent = new PullRequestEvent(prUrl, branchName, className, commitMessage, Instant.now());
        eventPublisher.publishEvent(prEvent);

        log.info("Auto-fix PR created for {}: {}", className, prUrl);
    }
}
