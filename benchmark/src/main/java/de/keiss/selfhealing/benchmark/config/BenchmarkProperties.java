package de.keiss.selfhealing.benchmark.config;

import de.keiss.selfhealing.benchmark.model.TestTrack;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for benchmark metric collection during test runs.
 *
 * <p>
 * When {@code benchmark.enabled=true}, the {@code HealingMetricsCollector} is registered and listens for
 * {@code HealingEvent}s emitted by the orchestrator. At the end of the Spring context lifecycle (ContextClosedEvent),
 * the collector writes a {@code BenchmarkRun} JSON fragment to {@link #outputDir()}.
 *
 * <p>
 * The {@link #trackName()} and {@link #difficulty()} are typically injected from the orchestration layer (e.g. via
 * {@code -Dbenchmark.track-name=...}) so a single test execution can be tagged with the track it belongs to.
 */
@ConfigurationProperties(prefix = "benchmark")
public record BenchmarkProperties(boolean enabled, String trackName, TestTrack.Difficulty difficulty,
        String outputDir) {

    public BenchmarkProperties {
        if (trackName == null || trackName.isBlank())
            trackName = "unknown";
        if (difficulty == null)
            difficulty = TestTrack.Difficulty.MEDIUM;
        if (outputDir == null || outputDir.isBlank())
            outputDir = "build/reports/benchmark";
    }

    public static BenchmarkProperties defaults() {
        return new BenchmarkProperties(false, "unknown", TestTrack.Difficulty.MEDIUM, "build/reports/benchmark");
    }
}
