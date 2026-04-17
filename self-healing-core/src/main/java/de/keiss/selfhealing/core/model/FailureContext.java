package de.keiss.selfhealing.core.model;

import org.openqa.selenium.By;

/**
 * All context collected when a test step fails — passed to healing agents for analysis.
 *
 * The optional {@code additionalContext} carries free-form diagnostic notes produced by upstream
 * enrichers (e.g. the MCP enricher) so they actually reach the LocatorHealer instead of being
 * discarded. Keep it short; it is appended verbatim to the user prompt.
 */
public record FailureContext(String exceptionMessage, String pageSourceXml, byte[] screenshot, By failedLocator,
        String pageObjectSource, String pageObjectClassName, String stepDefinitionSource, String stepName,
        String additionalContext) {

    public FailureContext(String exceptionMessage, String pageSourceXml, byte[] screenshot, By failedLocator,
            String pageObjectSource, String pageObjectClassName, String stepDefinitionSource, String stepName) {
        this(exceptionMessage, pageSourceXml, screenshot, failedLocator, pageObjectSource, pageObjectClassName,
                stepDefinitionSource, stepName, null);
    }

    public FailureContext withPageSource(String pageSourceXml) {
        return new FailureContext(exceptionMessage, pageSourceXml, screenshot, failedLocator, pageObjectSource,
                pageObjectClassName, stepDefinitionSource, stepName, additionalContext);
    }

    public FailureContext withScreenshot(byte[] screenshot) {
        return new FailureContext(exceptionMessage, pageSourceXml, screenshot, failedLocator, pageObjectSource,
                pageObjectClassName, stepDefinitionSource, stepName, additionalContext);
    }

    public FailureContext withAdditionalContext(String additionalContext) {
        return new FailureContext(exceptionMessage, pageSourceXml, screenshot, failedLocator, pageObjectSource,
                pageObjectClassName, stepDefinitionSource, stepName, additionalContext);
    }
}
