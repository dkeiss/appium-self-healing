package de.keiss.selfhealing.core.healing;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import de.keiss.selfhealing.core.config.SelfHealingProperties;
import de.keiss.selfhealing.core.model.BugReport;
import de.keiss.selfhealing.core.model.BugReport.Severity;
import de.keiss.selfhealing.core.model.BugReportEvent;
import de.keiss.selfhealing.core.model.FailureContext;
import de.keiss.selfhealing.core.model.TriageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handler for {@code APP_BUG} triage results.
 *
 * Produces a structured {@link BugReport}, persists it as JSON under the configured reports directory, writes the
 * associated screenshot next to it, and publishes a {@link BugReportEvent} for external consumers. The orchestrator
 * then returns a failed HealingResult — the test fails on purpose because the bug is in the app, not the test.
 */
@Slf4j
public class AppBugReporter {

    private final SelfHealingProperties.BugReports config;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper jsonMapper;

    public AppBugReporter(SelfHealingProperties.BugReports config, ApplicationEventPublisher eventPublisher) {
        this.config = config;
        this.eventPublisher = eventPublisher;
        this.jsonMapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    }

    public BugReport report(FailureContext context, TriageResult triage, String llmProvider) {
        BugReport bugReport = build(context, triage, llmProvider);
        log.warn("App bug detected: {} [{}] — {}", bugReport.title(), bugReport.severity(), bugReport.description());

        if (config.persistJson()) {
            persist(bugReport, context);
        }
        eventPublisher.publishEvent(new BugReportEvent(bugReport));
        return bugReport;
    }

    private BugReport build(FailureContext context, TriageResult triage, String llmProvider) {
        String id = "BUG-"
                + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                        .format(Instant.now().atZone(java.time.ZoneId.systemDefault()))
                + "-" + UUID.randomUUID().toString().substring(0, 6);

        String title = context.stepName() != null ? "Bug detected during step: " + context.stepName()
                : "App bug detected during test";

        String description = "Triage classified this failure as APP_BUG (confidence="
                + String.format("%.0f%%", triage.confidence() * 100) + "). "
                + (context.exceptionMessage() != null ? "Observed error: " + context.exceptionMessage() : "");

        Severity severity = inferSeverity(triage);
        List<String> reproSteps = buildReproductionSteps(context);

        return new BugReport(id, Instant.now(), context.stepName(), context.stepName(), severity, title, description,
                triage.reasoning(), triage.confidence(), context.exceptionMessage(),
                context.failedLocator() != null ? context.failedLocator().toString() : null,
                context.pageObjectClassName(), reproSteps, null, // set later by persist()
                llmProvider);
    }

    private Severity inferSeverity(TriageResult triage) {
        if (triage.confidence() >= 0.9)
            return Severity.HIGH;
        if (triage.confidence() >= 0.7)
            return Severity.MEDIUM;
        return Severity.LOW;
    }

    private List<String> buildReproductionSteps(FailureContext context) {
        List<String> steps = new ArrayList<>();
        if (context.stepName() != null) {
            steps.add("Execute scenario step: " + context.stepName());
        }
        if (context.pageObjectClassName() != null) {
            steps.add("Page object involved: " + context.pageObjectClassName());
        }
        if (context.failedLocator() != null) {
            steps.add("Failed locator: " + context.failedLocator());
        }
        if (context.exceptionMessage() != null) {
            steps.add("Observed error: " + context.exceptionMessage());
        }
        return steps;
    }

    private void persist(BugReport bugReport, FailureContext context) {
        try {
            Path reportsDir = Path.of(config.outputPath());
            Files.createDirectories(reportsDir);

            String screenshotRelPath = null;
            if (context.screenshot() != null && context.screenshot().length > 0) {
                Path screenshotFile = reportsDir.resolve(bugReport.id() + ".png");
                Files.write(screenshotFile, context.screenshot());
                screenshotRelPath = screenshotFile.getFileName().toString();
                bugReport = withScreenshotPath(bugReport, screenshotRelPath);
            }

            Path jsonFile = reportsDir.resolve(bugReport.id() + ".json");
            jsonMapper.writeValue(jsonFile.toFile(), bugReport);
            log.info("Bug report persisted: {}", jsonFile.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to persist bug report {}", bugReport.id(), e);
        }
    }

    private BugReport withScreenshotPath(BugReport original, String screenshotPath) {
        return new BugReport(original.id(), original.detectedAt(), original.scenarioName(), original.stepName(),
                original.severity(), original.title(), original.description(), original.triageReasoning(),
                original.triageConfidence(), original.exceptionMessage(), original.failedLocator(),
                original.pageObjectClassName(), original.reproductionSteps(), screenshotPath, original.llmProvider());
    }
}
