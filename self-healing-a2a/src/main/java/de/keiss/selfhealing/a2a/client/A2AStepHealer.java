package de.keiss.selfhealing.a2a.client;

import de.keiss.selfhealing.a2a.dto.DtoMapper;
import de.keiss.selfhealing.a2a.dto.FailureContextDto;
import de.keiss.selfhealing.a2a.dto.StepHealingResultDto;
import de.keiss.selfhealing.core.healing.StepHealer;
import de.keiss.selfhealing.core.healing.StepHealingResult;
import de.keiss.selfhealing.core.model.FailureContext;
import de.keiss.selfhealing.core.model.HealingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link StepHealer} proxy that forwards the step-healing request to a remote A2A agent.
 */
@Slf4j
@RequiredArgsConstructor
public class A2AStepHealer implements StepHealer {

    private final A2AClient client;

    @Override
    public StepHealingResult heal(FailureContext context) {
        long started = System.currentTimeMillis();
        try {
            FailureContextDto inputDto = DtoMapper.toDto(context);
            StepHealingResultDto outputDto = client.healStep(inputDto);
            return DtoMapper.fromDto(outputDto);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - started;
            log.error("A2A step-heal call failed after {}ms", duration, e);
            return StepHealingResult.failed("A2A call failed: " + e.getMessage(), duration);
        }
    }

    @Override
    public HealingResult toHealingResult(StepHealingResult stepResult) {
        return new HealingResult(stepResult.success(), null, null, stepResult.fixedPageObjectSource(),
                stepResult.explanation(), stepResult.healingDurationMs(), 0);
    }
}
