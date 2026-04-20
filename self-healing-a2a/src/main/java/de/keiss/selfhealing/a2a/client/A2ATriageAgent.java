package de.keiss.selfhealing.a2a.client;

import de.keiss.selfhealing.a2a.dto.DtoMapper;
import de.keiss.selfhealing.a2a.dto.FailureContextDto;
import de.keiss.selfhealing.a2a.dto.TriageResultDto;
import de.keiss.selfhealing.core.agent.TriageAgent;
import de.keiss.selfhealing.core.model.FailureContext;
import de.keiss.selfhealing.core.model.TriageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link TriageAgent} proxy that forwards the triage request to a remote A2A agent.
 */
@Slf4j
@RequiredArgsConstructor
public class A2ATriageAgent implements TriageAgent {

    private final A2AClient client;

    @Override
    public TriageResult analyze(FailureContext context) {
        long started = System.currentTimeMillis();
        try {
            FailureContextDto inputDto = DtoMapper.toDto(context);
            TriageResultDto outputDto = client.triageFailure(inputDto);
            return DtoMapper.fromDto(outputDto);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - started;
            log.error("A2A triage call failed after {}ms — defaulting to LOCATOR_CHANGED", duration, e);
            return new TriageResult(TriageResult.FailureCategory.LOCATOR_CHANGED,
                    "A2A triage call failed: " + e.getMessage(), 0.5);
        }
    }
}
