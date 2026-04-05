package de.keiss.selfhealing.core.model;

import org.openqa.selenium.By;

/**
 * All context collected when a test step fails — passed to healing agents for analysis.
 */
public record FailureContext(String exceptionMessage, String pageSourceXml, byte[] screenshot, By failedLocator,
        String pageObjectSource, String pageObjectClassName, String stepDefinitionSource, String stepName) {

    public FailureContext withPageSource(String pageSourceXml) {
        return new FailureContext(exceptionMessage, pageSourceXml, screenshot, failedLocator, pageObjectSource,
                pageObjectClassName, stepDefinitionSource, stepName);
    }

    public FailureContext withScreenshot(byte[] screenshot) {
        return new FailureContext(exceptionMessage, pageSourceXml, screenshot, failedLocator, pageObjectSource,
                pageObjectClassName, stepDefinitionSource, stepName);
    }
}
