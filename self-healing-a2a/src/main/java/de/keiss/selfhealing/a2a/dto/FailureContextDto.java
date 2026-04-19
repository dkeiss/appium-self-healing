package de.keiss.selfhealing.a2a.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Wire-friendly mirror of {@link de.keiss.selfhealing.core.model.FailureContext}. {@code By} instances are replaced by
 * their {@code toString()} form — the prompt creator only calls {@code toString()} on them, so no structural info is
 * lost. Screenshot is carried as base64.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FailureContextDto(String exceptionMessage, String pageSourceXml, String screenshotBase64,
        String failedLocatorText, String pageObjectSource, String pageObjectClassName, String stepDefinitionSource,
        String stepName, String additionalContext, List<String> rejectedLocatorTexts) {
}
