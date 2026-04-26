package de.keiss.selfhealing.benchmark.collector;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.keiss.selfhealing.benchmark.config.BenchmarkProperties;
import de.keiss.selfhealing.benchmark.model.TestTrack;
import de.keiss.selfhealing.core.model.HealingEvent;
import de.keiss.selfhealing.core.model.HealingResult;
import de.keiss.selfhealing.core.model.TriageResult.FailureCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HealingMetricsCollectorTest {

    @Test
    void collectsEventsAndWritesFragmentWithAggregateSummary(@TempDir Path tempDir) throws Exception {
        // --- Arrange ---
        var props = new BenchmarkProperties(true, "Einfache Locator-Änderungen", TestTrack.Difficulty.EASY,
                tempDir.toString());
        var collector = new HealingMetricsCollector(props);

        // --- Act: feed three events (2 successful, 1 failed) ---
        collector.onHealingEvent(successEvent("input_from", "departure_station", 1200, 850));
        collector.onHealingEvent(successEvent("btn_search", "fab_search", 1800, 920));
        collector.onHealingEvent(failedEvent("connection_list", 2500, 1100));

        Path fragmentPath = collector.flush();

        // --- Assert: file exists and shape is correct ---
        assertThat(fragmentPath).as("flush must return the written file path").isNotNull().exists();

        assertThat(fragmentPath.getFileName()).as("filename should encode provider and slugified track name")
                .hasToString("anthropic-einfache-locator-nderungen.json");

        var mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(fragmentPath.toFile());

        assertThat(root.get("llmProvider").asString()).isEqualTo("anthropic");
        assertThat(root.get("trackName").asString()).isEqualTo("Einfache Locator-Änderungen");
        assertThat(root.get("difficulty").asString()).isEqualTo("EASY");

        JsonNode metrics = root.get("healingMetrics");
        assertThat(metrics.isArray()).isTrue();
        assertThat(metrics).hasSize(3);
        assertThat(metrics.get(0).get("originalLocator").stringValue()).contains("input_from");

        JsonNode summary = root.get("summary");
        assertThat(summary.get("totalHealingAttempts").asInt()).isEqualTo(3);
        assertThat(summary.get("successfulHealings").asInt()).isEqualTo(2);
        assertThat(summary.get("failedHealings").asInt()).isEqualTo(1);
        assertThat(summary.get("successRate").asDouble()).isCloseTo(0.666, within(0.01));
        assertThat(summary.get("totalTokensUsed").asInt()).isEqualTo(850 + 920 + 1100);

        // anthropic: $3/1M → 2870 tokens ≈ $0.00861
        assertThat(summary.get("estimatedCostUsd").asDouble())
                .as("cost should use anthropic pricing from TokenCostCalculator").isCloseTo(0.00861, within(0.0001));
    }

    @Test
    void emptyEventList_skipsWriteAndReturnsNull(@TempDir Path tempDir) {
        var props = new BenchmarkProperties(true, "empty-track", TestTrack.Difficulty.EASY, tempDir.toString());
        var collector = new HealingMetricsCollector(props);

        Path result = collector.flush();

        assertThat(result).isNull();
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void collectedEventsSnapshotReflectsReceivedEvents(@TempDir Path tempDir) {
        var props = new BenchmarkProperties(true, "t", TestTrack.Difficulty.MEDIUM, tempDir.toString());
        var collector = new HealingMetricsCollector(props);

        collector.onHealingEvent(successEvent("a", "b", 10, 50));
        collector.onHealingEvent(successEvent("c", "d", 20, 60));

        assertThat(collector.collectedEvents()).hasSize(2).extracting(HealingEvent::originalLocator)
                .containsExactly("By.id: a", "By.id: c");
    }

    // --- Helpers: no Selenium dependency in benchmark — healedLocator is null, string form is literal ---

    private static HealingEvent successEvent(String original, String healed, long durationMs, int tokens) {
        var result = new HealingResult(true, null, "By.id(\"" + healed + "\")", null,
                "Healed " + original + " → " + healed, durationMs, tokens);
        return new HealingEvent(Instant.now(), "Direkte Verbindung finden", FailureCategory.LOCATOR_CHANGED, result,
                "By.id: " + original, "anthropic");
    }

    private static HealingEvent failedEvent(String original, long durationMs, int tokens) {
        var result = new HealingResult(false, null, null, null, "Could not find replacement for " + original,
                durationMs, tokens);
        return new HealingEvent(Instant.now(), "Direkte Verbindung finden", FailureCategory.LOCATOR_CHANGED, result,
                "By.id: " + original, "anthropic");
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
