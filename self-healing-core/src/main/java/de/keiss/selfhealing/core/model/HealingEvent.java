package de.keiss.selfhealing.core.model;

import java.time.Instant;

/**
 * Published after every healing attempt — consumed by reporters, metrics collectors, and the auto-fix PR creator.
 */
public record HealingEvent(Instant timestamp, String scenarioName, TriageResult.FailureCategory category,
        HealingResult healingResult, String originalLocator, String llmProvider, String pageObjectClassName,
        String pageObjectFilePath) {

    /**
     * Backwards-compatible factory for callers that don't have page-object metadata.
     */
    public HealingEvent(Instant timestamp, String scenarioName, TriageResult.FailureCategory category,
            HealingResult healingResult, String originalLocator, String llmProvider) {
        this(timestamp, scenarioName, category, healingResult, originalLocator, llmProvider, null, null);
    }
}
