package de.keiss.selfhealing.core.healing;

public record StepHealingResult(boolean success, String fixedMethodSource, String fixedPageObjectSource,
        String explanation, boolean requiresNewStep, String newStepSuggestion, long healingDurationMs) {

    public static StepHealingResult failed(String explanation, long durationMs) {
        return new StepHealingResult(false, null, null, explanation, false, null, durationMs);
    }
}
