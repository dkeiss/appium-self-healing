package de.keiss.selfhealing.benchmark.cost;

import java.util.Map;

/**
 * Estimates LLM cost in USD based on token counts and provider.
 *
 * <p>
 * Uses <b>input-token prices</b> as a pessimistic approximation — {@code HealingResult} currently reports a single
 * {@code tokensUsed} count without splitting input/output. For self-healing prompts the input (page source, page object
 * code, exception stack) typically dominates, so input-price gives a reasonable upper bound.
 *
 * <p>
 * Prices are hardcoded as of 2026-04-05. Update the table when provider pricing changes or when the project needs
 * per-run configurable pricing.
 */
public final class TokenCostCalculator {

    /**
     * USD per 1 million input tokens.
     */
    private static final Map<String, Double> PRICE_PER_MILLION_TOKENS = Map.of("anthropic", 3.00, // Claude Sonnet 4.6
            "openai", 2.00, // GPT-4.1
            "mistral", 0.30, // Codestral
            "local", 0.00 // LM Studio (self-hosted)
    );

    private TokenCostCalculator() {
        // utility
    }

    /**
     * Estimates the cost in USD for a given number of tokens used by a provider.
     *
     * @param provider
     *            provider key (case-insensitive, e.g. "anthropic", "openai")
     * @param tokens
     *            total number of tokens consumed
     * @return estimated cost in USD; {@code 0.0} for unknown providers or non-positive token counts
     */
    public static double estimateCostUsd(String provider, int tokens) {
        if (provider == null || tokens <= 0) {
            return 0.0;
        }
        double pricePerMillion = PRICE_PER_MILLION_TOKENS.getOrDefault(provider.toLowerCase(), 0.0);
        return pricePerMillion * (tokens / 1_000_000.0);
    }

    /**
     * @return price in USD per 1M tokens for the given provider, or {@code 0.0} if unknown
     */
    public static double pricePerMillionTokens(String provider) {
        if (provider == null) {
            return 0.0;
        }
        return PRICE_PER_MILLION_TOKENS.getOrDefault(provider.toLowerCase(), 0.0);
    }

    /**
     * @return an immutable view of the full pricing table (for reporting / debugging)
     */
    public static Map<String, Double> pricingTable() {
        return PRICE_PER_MILLION_TOKENS;
    }
}
