package de.keiss.selfhealing.core.healing;

import de.keiss.selfhealing.core.model.FailureContext;
import de.keiss.selfhealing.core.model.HealingResult;

public interface StepHealer {

    StepHealingResult heal(FailureContext context);

    HealingResult toHealingResult(StepHealingResult stepResult);
}
