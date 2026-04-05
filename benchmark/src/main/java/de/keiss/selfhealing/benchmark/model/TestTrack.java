package de.keiss.selfhealing.benchmark.model;

import java.util.List;

/**
 * Defines a test track for LLM comparison benchmarks. Loaded from YAML files in resources/tracks/.
 */
public record TestTrack(String name, String description, String appVersion, Difficulty difficulty,
        List<TrackScenario> scenarios, List<String> metrics) {

    public enum Difficulty {
        EASY, MEDIUM, HARD
    }

    public record TrackScenario(String feature, String scenario, int expectedHealings, long maxHealingTimeMs,
            List<BrokenLocator> brokenLocators) {
    }

    public record BrokenLocator(String original, String expected, String type, String description) {
    }
}
