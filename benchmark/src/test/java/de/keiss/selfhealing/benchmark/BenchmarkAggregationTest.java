package de.keiss.selfhealing.benchmark;

import tools.jackson.databind.ObjectMapper;
import de.keiss.selfhealing.benchmark.model.BenchmarkReport;
import de.keiss.selfhealing.benchmark.model.BenchmarkRun;
import de.keiss.selfhealing.benchmark.model.BenchmarkRun.BenchmarkSummary;
import de.keiss.selfhealing.benchmark.model.BenchmarkRun.HealingMetrics;
import de.keiss.selfhealing.benchmark.model.TestTrack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkAggregationTest {

    @Test
    void loadsMultipleFragmentsAndAggregatesPerProviderSummaries(@TempDir Path tempDir) throws Exception {
        // --- Arrange: write two fragments (two providers, same track) ---
        var mapper = new ObjectMapper();

        BenchmarkRun anthropicRun = buildRun("anthropic", "easy-locator", TestTrack.Difficulty.EASY,
                List.of(successMetric("input_from", "departure_station", 1200, 850),
                        successMetric("btn_search", "fab_search", 1500, 900),
                        successMetric("input_to", "arrival_station", 1100, 820)));
        BenchmarkRun mistralRun = buildRun("mistral", "easy-locator", TestTrack.Difficulty.EASY,
                List.of(successMetric("input_from", "departure_station", 800, 600), failMetric("btn_search", 900, 700),
                        successMetric("input_to", "arrival_station", 850, 650)));

        mapper.writerWithDefaultPrettyPrinter().writeValue(tempDir.resolve("anthropic-easy-locator.json").toFile(),
                anthropicRun);
        mapper.writerWithDefaultPrettyPrinter().writeValue(tempDir.resolve("mistral-easy-locator.json").toFile(),
                mistralRun);

        // --- Act ---
        var runner = new BenchmarkRunner();
        List<BenchmarkRun> loaded = runner.loadRunFragments(tempDir);
        BenchmarkReport report = BenchmarkReport.generate(loaded);

        // --- Assert ---
        assertThat(loaded).as("both fragment files should be loaded").hasSize(2).extracting(BenchmarkRun::llmProvider)
                .containsExactlyInAnyOrder("anthropic", "mistral");

        assertThat(report.providerSummaries()).containsKeys("anthropic", "mistral");

        var anthropicSummary = report.providerSummaries().get("anthropic");
        assertThat(anthropicSummary.overallSuccessRate()).as("anthropic had 3/3 successful heals").isEqualTo(1.0);
        assertThat(anthropicSummary.totalTokens()).isEqualTo(850 + 900 + 820);
        assertThat(anthropicSummary.estimatedCostUsd())
                .as("cost must be computed from TokenCostCalculator (anthropic: $3/1M)").isGreaterThan(0.0);

        var mistralSummary = report.providerSummaries().get("mistral");
        assertThat(mistralSummary.overallSuccessRate()).as("mistral had 2/3 successful heals").isCloseTo(0.666,
                within(0.01));
        assertThat(mistralSummary.totalTokens()).isEqualTo(600 + 700 + 650);
        assertThat(mistralSummary.estimatedCostUsd())
                .as("mistral pricing ($0.30/1M) is lower than anthropic for same token budget")
                .isLessThan(anthropicSummary.estimatedCostUsd());

        // Difficulty breakdown
        assertThat(anthropicSummary.successRateByDifficulty()).containsEntry(TestTrack.Difficulty.EASY, 1.0);
        assertThat(mistralSummary.successRateByDifficulty()).containsEntry(TestTrack.Difficulty.EASY, 2.0 / 3.0);
    }

    @Test
    void missingDirectoryYieldsEmptyList(@TempDir Path tempDir) throws IOException {
        var runner = new BenchmarkRunner();
        Path nonExistent = tempDir.resolve("does-not-exist");

        List<BenchmarkRun> runs = runner.loadRunFragments(nonExistent);

        assertThat(runs).isEmpty();
    }

    @Test
    void malformedFragmentIsSkippedButOthersLoad(@TempDir Path tempDir) throws Exception {
        var mapper = new ObjectMapper();

        BenchmarkRun good = buildRun("openai", "medium", TestTrack.Difficulty.MEDIUM,
                List.of(successMetric("x", "y", 100, 50)));
        mapper.writerWithDefaultPrettyPrinter().writeValue(tempDir.resolve("openai-medium.json").toFile(), good);
        java.nio.file.Files.writeString(tempDir.resolve("broken.json"), "{ not valid json");

        var runner = new BenchmarkRunner();
        List<BenchmarkRun> runs = runner.loadRunFragments(tempDir);

        assertThat(runs).as("the valid fragment must still be loaded even though another is malformed").hasSize(1)
                .first().extracting(BenchmarkRun::llmProvider).isEqualTo("openai");
    }

    @Test
    void benchmarkReportJsonFileIsExcludedFromFragmentScan(@TempDir Path tempDir) throws Exception {
        var mapper = new ObjectMapper();

        BenchmarkRun fragment = buildRun("anthropic", "t", TestTrack.Difficulty.HARD,
                List.of(successMetric("a", "b", 10, 50)));
        mapper.writerWithDefaultPrettyPrinter().writeValue(tempDir.resolve("anthropic-t.json").toFile(), fragment);

        // Simulate a leftover aggregate report from a previous run — must not be
        // re-loaded as a fragment (would double-count)
        BenchmarkReport stale = BenchmarkReport.generate(List.of(fragment));
        mapper.writerWithDefaultPrettyPrinter().writeValue(tempDir.resolve("benchmark-report.json").toFile(), stale);

        var runner = new BenchmarkRunner();
        List<BenchmarkRun> runs = runner.loadRunFragments(tempDir);

        assertThat(runs).as("benchmark-report.json is the aggregate output and must be ignored").hasSize(1);
    }

    // --- Helpers ------------------------------------------------------------

    private static BenchmarkRun buildRun(String provider, String track, TestTrack.Difficulty difficulty,
            List<HealingMetrics> metrics) {
        Instant start = Instant.parse("2026-04-05T10:00:00Z");
        Instant end = Instant.parse("2026-04-05T10:01:00Z");
        BenchmarkSummary summary = BenchmarkSummary.fromMetrics(metrics, provider);
        return new BenchmarkRun(provider, track, difficulty, start, end, 60_000L, metrics, summary);
    }

    private static HealingMetrics successMetric(String original, String healed, long durationMs, int tokens) {
        return new HealingMetrics("Test scenario", "By.id: " + original, "By.id(\"" + healed + "\")", true, durationMs,
                tokens, "Healed " + original + " → " + healed);
    }

    private static HealingMetrics failMetric(String original, long durationMs, int tokens) {
        return new HealingMetrics("Test scenario", "By.id: " + original, null, false, durationMs, tokens,
                "No replacement found");
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
