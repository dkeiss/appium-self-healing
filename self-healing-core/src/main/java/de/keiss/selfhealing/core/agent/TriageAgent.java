package de.keiss.selfhealing.core.agent;

import de.keiss.selfhealing.core.model.FailureContext;
import de.keiss.selfhealing.core.model.TriageResult;

public interface TriageAgent {
    TriageResult analyze(FailureContext context);
}
