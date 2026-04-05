package de.keiss.selfhealing.benchmark.cost;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TokenCostCalculatorTest {

    @Test
    void knownProviders_returnExpectedPricePerMillion() {
        assertThat(TokenCostCalculator.pricePerMillionTokens("anthropic")).isEqualTo(3.00);
        assertThat(TokenCostCalculator.pricePerMillionTokens("openai")).isEqualTo(2.00);
        assertThat(TokenCostCalculator.pricePerMillionTokens("mistral")).isEqualTo(0.30);
        assertThat(TokenCostCalculator.pricePerMillionTokens("local")).isEqualTo(0.00);
    }

    @Test
    void providerLookupIsCaseInsensitive() {
        assertThat(TokenCostCalculator.pricePerMillionTokens("ANTHROPIC")).isEqualTo(3.00);
        assertThat(TokenCostCalculator.pricePerMillionTokens("OpenAI")).isEqualTo(2.00);
    }

    @Test
    void unknownProvider_yieldsZeroCost() {
        assertThat(TokenCostCalculator.pricePerMillionTokens("gemini")).isZero();
        assertThat(TokenCostCalculator.estimateCostUsd("gemini", 100_000)).isZero();
    }

    @Test
    void nullProvider_yieldsZeroCost() {
        assertThat(TokenCostCalculator.pricePerMillionTokens(null)).isZero();
        assertThat(TokenCostCalculator.estimateCostUsd(null, 50_000)).isZero();
    }

    @Test
    void costScalesLinearlyWithTokens() {
        // Tolerances account for the unavoidable floating-point drift in
        // (tokens / 1_000_000.0) * pricePerMillion.
        // Claude Sonnet 4.6: $3 per 1M tokens → 100k tokens should cost $0.30
        assertThat(TokenCostCalculator.estimateCostUsd("anthropic", 100_000)).isCloseTo(0.30, within(1e-9));

        // GPT-4.1: $2.00 per 1M → 1M tokens = $2.00
        assertThat(TokenCostCalculator.estimateCostUsd("openai", 1_000_000)).isCloseTo(2.00, within(1e-9));

        // Mistral Codestral: $0.30/1M → 500k = $0.15
        assertThat(TokenCostCalculator.estimateCostUsd("mistral", 500_000)).isCloseTo(0.15, within(1e-9));
    }

    @Test
    void zeroOrNegativeTokens_yieldZeroCost() {
        assertThat(TokenCostCalculator.estimateCostUsd("anthropic", 0)).isZero();
        assertThat(TokenCostCalculator.estimateCostUsd("anthropic", -100)).isZero();
    }

    @Test
    void localProviderIsAlwaysFree() {
        assertThat(TokenCostCalculator.estimateCostUsd("local", 10_000_000)).isZero();
    }

    @Test
    void pricingTableContainsAllCoreProviders() {
        assertThat(TokenCostCalculator.pricingTable()).containsKeys("anthropic", "openai", "mistral", "local");
    }
}
