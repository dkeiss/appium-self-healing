package de.keiss.selfhealing.core.model;

import org.openqa.selenium.By;

public record HealingResult(boolean success, By healedLocator, String healedLocatorExpression,
        String fixedPageObjectSource, String explanation, long healingDurationMs, int tokensUsed) {

    public static HealingResult failed(String explanation, long durationMs) {
        return new HealingResult(false, null, null, null, explanation, durationMs, 0);
    }
}
