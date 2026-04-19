package de.keiss.selfhealing.a2a.client;

import de.keiss.selfhealing.a2a.dto.DtoMapper;
import de.keiss.selfhealing.a2a.dto.FailureContextDto;
import de.keiss.selfhealing.a2a.dto.HealingResultDto;
import de.keiss.selfhealing.core.healing.LocatorHealer;
import de.keiss.selfhealing.core.model.FailureContext;
import de.keiss.selfhealing.core.model.HealingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link LocatorHealer} implementation that forwards the heal request to a remote A2A agent. Marshals
 * {@link FailureContext} to {@link FailureContextDto}, sends it via {@link A2AClient}, and converts the returned
 * {@link HealingResultDto} back into a domain {@link HealingResult}.
 */
@Slf4j
@RequiredArgsConstructor
public class A2ALocatorHealer implements LocatorHealer {

    private final A2AClient client;

    @Override
    public HealingResult heal(FailureContext context) {
        long started = System.currentTimeMillis();
        try {
            FailureContextDto inputDto = DtoMapper.toDto(context);
            HealingResultDto outputDto = client.healLocator(inputDto);
            return DtoMapper.fromDto(outputDto);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - started;
            log.error("A2A heal call failed after {}ms", duration, e);
            return HealingResult.failed("A2A call failed: " + e.getMessage(), duration);
        }
    }
}
