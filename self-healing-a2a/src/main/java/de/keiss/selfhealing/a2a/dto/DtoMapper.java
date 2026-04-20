package de.keiss.selfhealing.a2a.dto;

import de.keiss.selfhealing.core.healing.LocatorFactory;
import de.keiss.selfhealing.core.healing.StepHealingResult;
import de.keiss.selfhealing.core.model.FailureContext;
import de.keiss.selfhealing.core.model.HealingResult;
import de.keiss.selfhealing.core.model.TriageResult;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import java.util.Base64;
import java.util.List;

/**
 * Maps between core domain records and the wire DTOs. The tricky part is {@link By} — Selenium locators are not
 * meaningfully JSON-serializable, so the DTO carries the string form and we wrap it in a pass-through {@link By}
 * implementation server-side. That is enough because the prompt creator only calls {@code toString()} on the
 * {@code failedLocator} and on entries in {@code rejectedLocators}.
 */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static FailureContextDto toDto(FailureContext ctx) {
        return new FailureContextDto(ctx.exceptionMessage(), ctx.pageSourceXml(),
                ctx.screenshot() != null && ctx.screenshot().length > 0
                        ? Base64.getEncoder().encodeToString(ctx.screenshot()) : null,
                ctx.failedLocator() != null ? ctx.failedLocator().toString() : null, ctx.pageObjectSource(),
                ctx.pageObjectClassName(), ctx.stepDefinitionSource(), ctx.stepName(), ctx.additionalContext(),
                ctx.rejectedLocators() != null ? ctx.rejectedLocators().stream().map(By::toString).toList()
                        : List.of());
    }

    public static FailureContext fromDto(FailureContextDto dto) {
        byte[] screenshot = dto.screenshotBase64() != null ? Base64.getDecoder().decode(dto.screenshotBase64()) : null;
        By failedLocator = dto.failedLocatorText() != null ? new StringBy(dto.failedLocatorText()) : null;
        List<By> rejected = dto.rejectedLocatorTexts() != null
                ? dto.rejectedLocatorTexts().stream().<By>map(StringBy::new).toList() : List.of();
        return new FailureContext(dto.exceptionMessage(), dto.pageSourceXml(), screenshot, failedLocator,
                dto.pageObjectSource(), dto.pageObjectClassName(), dto.stepDefinitionSource(), dto.stepName(),
                dto.additionalContext(), rejected);
    }

    public static HealingResultDto toDto(HealingResult result) {
        String[] parts = splitExpression(result.healedLocatorExpression());
        return new HealingResultDto(result.success(), parts[0], parts[1], result.healedLocatorExpression(),
                result.fixedPageObjectSource(), result.explanation(), result.healingDurationMs(), result.tokensUsed());
    }

    public static HealingResult fromDto(HealingResultDto dto) {
        if (!dto.success()) {
            return new HealingResult(false, null, null, dto.fixedPageObjectSource(), dto.explanation(),
                    dto.healingDurationMs(), dto.tokensUsed());
        }
        try {
            By healedLocator = LocatorFactory.construct(dto.healedLocatorMethod(), dto.healedLocatorValue());
            return new HealingResult(true, healedLocator, dto.healedLocatorExpression(), dto.fixedPageObjectSource(),
                    dto.explanation(), dto.healingDurationMs(), dto.tokensUsed());
        } catch (Exception e) {
            return HealingResult.failed("A2A client could not reconstruct By from (" + dto.healedLocatorMethod() + ", "
                    + dto.healedLocatorValue() + "): " + e.getMessage(), dto.healingDurationMs());
        }
    }

    /**
     * Splits the {@code method("value")} expression produced by the healer. Returns {@code [method, value]}. If the
     * input is null or malformed, returns {@code [null, null]} and the client-side reconstruction will fail loudly.
     */
    private static String[] splitExpression(String expression) {
        if (expression == null) {
            return new String[]{null, null};
        }
        int openParen = expression.indexOf('(');
        int closeParen = expression.lastIndexOf(')');
        if (openParen < 0 || closeParen < openParen) {
            return new String[]{null, null};
        }
        String method = expression.substring(0, openParen);
        String value = expression.substring(openParen + 1, closeParen);
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return new String[]{method, value};
    }

    public static TriageResultDto toDto(TriageResult result) {
        return new TriageResultDto(result.category().name(), result.reasoning(), result.confidence());
    }

    public static TriageResult fromDto(TriageResultDto dto) {
        return new TriageResult(TriageResult.FailureCategory.valueOf(dto.category()), dto.reasoning(),
                dto.confidence());
    }

    public static StepHealingResultDto toDto(StepHealingResult result) {
        return new StepHealingResultDto(result.success(), result.fixedMethodSource(), result.fixedPageObjectSource(),
                result.explanation(), result.requiresNewStep(), result.newStepSuggestion(), result.healingDurationMs());
    }

    public static StepHealingResult fromDto(StepHealingResultDto dto) {
        return new StepHealingResult(dto.success(), dto.fixedMethodSource(), dto.fixedPageObjectSource(),
                dto.explanation(), dto.requiresNewStep(), dto.newStepSuggestion(), dto.healingDurationMs());
    }

    /**
     * Minimal {@link By} wrapper that just remembers a string form. Used to carry the failed locator (and previously
     * rejected locators) across the wire — we never need to actually search with it, only to feed its toString() into
     * the prompt.
     */
    private static final class StringBy extends By {

        private final String repr;

        StringBy(String repr) {
            this.repr = repr;
        }

        @Override
        public List<WebElement> findElements(SearchContext context) {
            throw new UnsupportedOperationException("StringBy is a wire-only placeholder and cannot search");
        }

        @Override
        public String toString() {
            return repr;
        }
    }
}
