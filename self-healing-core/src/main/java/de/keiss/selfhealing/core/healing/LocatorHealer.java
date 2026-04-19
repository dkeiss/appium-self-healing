package de.keiss.selfhealing.core.healing;

import de.keiss.selfhealing.core.model.FailureContext;
import de.keiss.selfhealing.core.model.HealingResult;

/**
 * Strategy interface for healing a broken locator. The default implementation ({@link ChatClientLocatorHealer}) calls
 * an LLM via Spring AI. Alternative implementations can delegate to a remote agent (A2A) without changing the
 * orchestrator.
 */
public interface LocatorHealer {

    HealingResult heal(FailureContext context);
}
