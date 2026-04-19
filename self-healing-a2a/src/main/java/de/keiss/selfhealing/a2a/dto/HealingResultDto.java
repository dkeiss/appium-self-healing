package de.keiss.selfhealing.a2a.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Wire-friendly mirror of {@link de.keiss.selfhealing.core.model.HealingResult}. The healed {@code By} is split into
 * {@code method} + {@code value} so the client can reconstruct it via
 * {@link de.keiss.selfhealing.core.healing.LocatorFactory}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HealingResultDto(boolean success, String healedLocatorMethod, String healedLocatorValue,
        String healedLocatorExpression, String fixedPageObjectSource, String explanation, long healingDurationMs,
        int tokensUsed) {
}
