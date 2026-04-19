package de.keiss.selfhealing.core.model;

import org.openqa.selenium.By;

import java.util.ArrayList;
import java.util.List;

/**
 * All context collected when a test step fails — passed to healing agents for analysis.
 *
 * The optional {@code additionalContext} carries free-form diagnostic notes produced by upstream enrichers (e.g. the
 * MCP enricher) so they actually reach the LocatorHealer instead of being discarded. Keep it short; it is appended
 * verbatim to the user prompt.
 *
 * {@code rejectedLocators} collects heal suggestions that were previously tried in the current retry loop but could not
 * be resolved against the UI. Passed to the LocatorHealer so the LLM does not propose them again.
 */
public record FailureContext(String exceptionMessage, String pageSourceXml, byte[] screenshot, By failedLocator,
        String pageObjectSource, String pageObjectClassName, String stepDefinitionSource, String stepName,
        String additionalContext, List<By> rejectedLocators) {

    public FailureContext(String exceptionMessage, String pageSourceXml, byte[] screenshot, By failedLocator,
            String pageObjectSource, String pageObjectClassName, String stepDefinitionSource, String stepName) {
        this(exceptionMessage, pageSourceXml, screenshot, failedLocator, pageObjectSource, pageObjectClassName,
                stepDefinitionSource, stepName, null, List.of());
    }

    public FailureContext(String exceptionMessage, String pageSourceXml, byte[] screenshot, By failedLocator,
            String pageObjectSource, String pageObjectClassName, String stepDefinitionSource, String stepName,
            String additionalContext) {
        this(exceptionMessage, pageSourceXml, screenshot, failedLocator, pageObjectSource, pageObjectClassName,
                stepDefinitionSource, stepName, additionalContext, List.of());
    }

    public FailureContext withPageSource(String pageSourceXml) {
        return new FailureContext(exceptionMessage, pageSourceXml, screenshot, failedLocator, pageObjectSource,
                pageObjectClassName, stepDefinitionSource, stepName, additionalContext, rejectedLocators);
    }

    public FailureContext withScreenshot(byte[] screenshot) {
        return new FailureContext(exceptionMessage, pageSourceXml, screenshot, failedLocator, pageObjectSource,
                pageObjectClassName, stepDefinitionSource, stepName, additionalContext, rejectedLocators);
    }

    public FailureContext withAdditionalContext(String additionalContext) {
        return new FailureContext(exceptionMessage, pageSourceXml, screenshot, failedLocator, pageObjectSource,
                pageObjectClassName, stepDefinitionSource, stepName, additionalContext, rejectedLocators);
    }

    public FailureContext withRejectedLocator(By rejected) {
        List<By> updated = new ArrayList<>(rejectedLocators);
        updated.add(rejected);
        return new FailureContext(exceptionMessage, pageSourceXml, screenshot, failedLocator, pageObjectSource,
                pageObjectClassName, stepDefinitionSource, stepName, additionalContext, List.copyOf(updated));
    }
}
