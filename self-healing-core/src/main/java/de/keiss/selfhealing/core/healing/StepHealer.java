package de.keiss.selfhealing.core.healing;

import de.keiss.selfhealing.core.config.SelfHealingProperties;
import de.keiss.selfhealing.core.model.FailureContext;
import de.keiss.selfhealing.core.model.HealingResult;
import de.keiss.selfhealing.core.prompt.StepPromptCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Heals step-level failures — when the test logic itself needs adjustment because the app's navigation flow or
 * interaction patterns changed.
 *
 * Unlike LocatorHealer (which fixes a single By locator at runtime), StepHealer suggests code-level changes to step
 * definitions and page objects.
 */
@Slf4j
@RequiredArgsConstructor
public class StepHealer {

    private final ChatClient chatClient;
    private final SelfHealingProperties properties;

    public StepHealingResult heal(FailureContext context) {
        long startTime = System.currentTimeMillis();
        log.info("StepHealer analyzing step failure: {}", context.stepName());

        try {
            var promptCreator = new StepPromptCreator();

            StepResponse response = chatClient.prompt().system(promptCreator.createSystemPrompt())
                    .user(promptCreator.createUserPrompt(context)).call().entity(StepResponse.class);

            if (response == null || response.fixedMethodSource() == null) {
                return StepHealingResult.failed("LLM returned no fix suggestion", elapsed(startTime));
            }

            long duration = elapsed(startTime);
            log.info("StepHealer produced fix for '{}' in {}ms", context.stepName(), duration);

            return new StepHealingResult(true, response.fixedMethodSource(), response.fixedPageObjectSource(),
                    response.explanation(), response.requiresNewStep(), response.newStepSuggestion(), duration);
        } catch (Exception e) {
            log.error("Step healing failed", e);
            return StepHealingResult.failed(e.getMessage(), elapsed(startTime));
        }
    }

    /**
     * Converts a step healing result to a general HealingResult for the pipeline. Step-level fixes are primarily code
     * suggestions, not runtime-applicable locators.
     */
    public HealingResult toHealingResult(StepHealingResult stepResult) {
        return new HealingResult(stepResult.success(), null, // no runtime locator fix
                null, stepResult.fixedPageObjectSource(), stepResult.explanation(), stepResult.healingDurationMs(), 0);
    }

    private long elapsed(long startTime) {
        return System.currentTimeMillis() - startTime;
    }

    public record StepHealingResult(boolean success, String fixedMethodSource, String fixedPageObjectSource,
            String explanation, boolean requiresNewStep, String newStepSuggestion, long healingDurationMs) {

        public static StepHealingResult failed(String explanation, long durationMs) {
            return new StepHealingResult(false, null, null, explanation, false, null, durationMs);
        }
    }

    private record StepResponse(String fixedMethodSource, String fixedPageObjectSource, String explanation,
            boolean requiresNewStep, String newStepSuggestion) {
    }
}
