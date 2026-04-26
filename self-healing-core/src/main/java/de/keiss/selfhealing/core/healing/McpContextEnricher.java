package de.keiss.selfhealing.core.healing;

import de.keiss.selfhealing.core.model.FailureContext;

public interface McpContextEnricher {

    FailureContext enrich(FailureContext context);
}
