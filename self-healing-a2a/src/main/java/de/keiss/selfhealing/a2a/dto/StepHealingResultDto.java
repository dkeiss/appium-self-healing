package de.keiss.selfhealing.a2a.dto;

public record StepHealingResultDto(boolean success, String fixedMethodSource, String fixedPageObjectSource,
        String explanation, boolean requiresNewStep, String newStepSuggestion, long healingDurationMs) {
}
