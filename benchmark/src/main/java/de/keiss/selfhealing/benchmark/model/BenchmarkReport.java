package de.keiss.selfhealing.benchmark.model;

import de.keiss.selfhealing.benchmark.cost.TokenCostCalculator;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aggregated comparison report across all LLM providers and test tracks.
 */
public record BenchmarkReport(Instant generatedAt, List<BenchmarkRun> runs,
        Map<String, ProviderSummary> providerSummaries) {

    public record ProviderSummary(String provider, double overallSuccessRate, long avgHealingTimeMs, int totalTokens,
            double estimatedCostUsd, Map<TestTrack.Difficulty, Double> successRateByDifficulty) {
    }

    public static BenchmarkReport generate(List<BenchmarkRun> runs) {
        Map<String, ProviderSummary> summaries = runs.stream().collect(Collectors.groupingBy(BenchmarkRun::llmProvider))
                .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> summarizeProvider(entry.getKey(), entry.getValue())));

        return new BenchmarkReport(Instant.now(), runs, summaries);
    }

    private static ProviderSummary summarizeProvider(String provider, List<BenchmarkRun> runs) {
        double overallSuccessRate = runs.stream().mapToDouble(r -> r.summary().successRate()).average().orElse(0.0);

        long avgTime = (long) runs.stream().mapToLong(r -> r.summary().avgHealingTimeMs()).average().orElse(0);

        int totalTokens = runs.stream().mapToInt(r -> r.summary().totalTokensUsed()).sum();

        double estimatedCost = TokenCostCalculator.estimateCostUsd(provider, totalTokens);

        Map<TestTrack.Difficulty, Double> byDifficulty = runs.stream().collect(Collectors
                .groupingBy(BenchmarkRun::difficulty, Collectors.averagingDouble(r -> r.summary().successRate())));

        return new ProviderSummary(provider, overallSuccessRate, avgTime, totalTokens, estimatedCost, byDifficulty);
    }

    /**
     * Generates a human-readable comparison table.
     */
    public String toComparisonTable() {
        var sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║               LLM SELF-HEALING BENCHMARK REPORT                     ║\n");
        sb.append("╠══════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ %-15s │ %8s │ %8s │ %8s │ %10s ║%n", "Provider", "Success%", "Avg ms", "Tokens",
                "Est. Cost"));
        sb.append("║─────────────────┼──────────┼──────────┼──────────┼────────────║\n");

        providerSummaries.values().stream()
                .sorted((a, b) -> Double.compare(b.overallSuccessRate(), a.overallSuccessRate()))
                .forEach(s -> sb.append(String.format("║ %-15s │ %7.1f%% │ %6dms │ %8d │ $%8.4f ║%n", s.provider(),
                        s.overallSuccessRate() * 100, s.avgHealingTimeMs(), s.totalTokens(), s.estimatedCostUsd())));

        sb.append("╠══════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║ Success Rate by Difficulty:                                         ║\n");

        providerSummaries.forEach((provider, summary) -> {
            sb.append(String.format("║   %-12s: ", provider));
            summary.successRateByDifficulty()
                    .forEach((diff, rate) -> sb.append(String.format("%s=%.0f%% ", diff, rate * 100)));
            sb.append(" ".repeat(Math.max(0, 50 - provider.length()))).append("║\n");
        });

        sb.append("╚══════════════════════════════════════════════════════════════════════╝\n");
        return sb.toString();
    }
}
