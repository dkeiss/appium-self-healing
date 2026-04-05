package de.keiss.selfhealing.benchmark.model;

import de.keiss.selfhealing.benchmark.cost.TokenCostCalculator;

import java.time.Instant;
import java.util.List;

/**
 * Records the results of a single benchmark run (one LLM provider + one test track).
 */
public record BenchmarkRun(String llmProvider, String trackName, TestTrack.Difficulty difficulty, Instant startTime,
        Instant endTime, long totalDurationMs, List<HealingMetrics> healingMetrics, BenchmarkSummary summary) {

    public record HealingMetrics(String scenarioName, String originalLocator, String healedLocator, boolean success,
            long healingTimeMs, int tokensUsed, String explanation) {
    }

    public record BenchmarkSummary(int totalHealingAttempts, int successfulHealings, int failedHealings,
            double successRate, long avgHealingTimeMs, long maxHealingTimeMs, int totalTokensUsed,
            double estimatedCostUsd) {

        /**
         * Aggregates a list of {@link HealingMetrics} into a summary. The {@code provider} parameter is used by
         * {@link TokenCostCalculator} to estimate USD cost based on total tokens consumed; pass {@code null} or an
         * unknown provider for a zero-cost summary.
         */
        public static BenchmarkSummary fromMetrics(List<HealingMetrics> metrics, String provider) {
            if (metrics.isEmpty()) {
                return new BenchmarkSummary(0, 0, 0, 0.0, 0, 0, 0, 0.0);
            }

            int total = metrics.size();
            int success = (int) metrics.stream().filter(HealingMetrics::success).count();
            int failed = total - success;
            double rate = (double) success / total;
            long avgTime = (long) metrics.stream().mapToLong(HealingMetrics::healingTimeMs).average().orElse(0);
            long maxTime = metrics.stream().mapToLong(HealingMetrics::healingTimeMs).max().orElse(0);
            int tokens = metrics.stream().mapToInt(HealingMetrics::tokensUsed).sum();
            double estimatedCost = TokenCostCalculator.estimateCostUsd(provider, tokens);

            return new BenchmarkSummary(total, success, failed, rate, avgTime, maxTime, tokens, estimatedCost);
        }
    }
}
