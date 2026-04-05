package de.keiss.selfhealing.core.model;

public record TriageResult(FailureCategory category, String reasoning, double confidence) {

    public enum FailureCategory {
        LOCATOR_CHANGED, TEST_LOGIC_ERROR, ENVIRONMENT_ISSUE, APP_BUG
    }
}
