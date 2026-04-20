package de.keiss.selfhealing.a2a.client;

import de.keiss.selfhealing.a2a.dto.DtoMapper;
import de.keiss.selfhealing.a2a.dto.FailureContextDto;
import de.keiss.selfhealing.core.healing.McpContextEnricher;
import de.keiss.selfhealing.core.model.FailureContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link McpContextEnricher} proxy that forwards the enrichment request to a remote A2A agent.
 */
@Slf4j
@RequiredArgsConstructor
public class A2AMcpContextEnricher implements McpContextEnricher {

    private final A2AClient client;

    @Override
    public FailureContext enrich(FailureContext context) {
        try {
            FailureContextDto inputDto = DtoMapper.toDto(context);
            FailureContextDto enrichedDto = client.enrichContext(inputDto);
            return DtoMapper.fromDto(enrichedDto);
        } catch (Exception e) {
            log.warn("A2A enrich-context call failed — continuing with original context: {}", e.getMessage());
            return context;
        }
    }
}
