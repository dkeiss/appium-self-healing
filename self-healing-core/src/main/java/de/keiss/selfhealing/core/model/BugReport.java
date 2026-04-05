package de.keiss.selfhealing.core.model;

import java.time.Instant;
import java.util.List;

/**
 * Structured report of an application bug detected by triage.
 *
 * AppBugReporter writes these as JSON under {@code build/reports/bugs/} and publishes a {@link BugReportEvent} so
 * external listeners (CI, issue trackers) can react. Unlike HealingResults, BugReports are terminal — the test fails
 * intentionally because the bug is in the app under test, not the test itself.
 */
public record BugReport(String id, Instant detectedAt, String scenarioName, String stepName, Severity severity,
        String title, String description, String triageReasoning, double triageConfidence, String exceptionMessage,
        String failedLocator, String pageObjectClassName, List<String> reproductionSteps, String screenshotPath,
        String llmProvider) {

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
