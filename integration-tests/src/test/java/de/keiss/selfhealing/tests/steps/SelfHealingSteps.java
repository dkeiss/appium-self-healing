package de.keiss.selfhealing.tests.steps;

import de.keiss.selfhealing.core.healing.HealingOrchestrator;
import de.keiss.selfhealing.core.healing.PromptCache;
import de.keiss.selfhealing.core.model.HealingEvent;
import de.keiss.selfhealing.core.model.HealingResult;
import io.cucumber.java.de.Angenommen;
import io.cucumber.java.de.Dann;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class SelfHealingSteps {

    private final HealingOrchestrator orchestrator;
    private final List<HealingEvent> healingEvents = new ArrayList<>();

    @Angenommen("Self-Healing ist aktiviert")
    public void selfHealingEnabled() {
        log.info("Self-Healing is enabled for this scenario");
        healingEvents.clear();
    }

    @Dann("der Self-Healing-Report zeigt alle geheilten Locatoren")
    public void showHealingReport() {
        PromptCache cache = orchestrator.getPromptCache();

        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║           SELF-HEALING REPORT                   ║");
        log.info("╠══════════════════════════════════════════════════╣");

        if (healingEvents.isEmpty()) {
            log.info("║  No healing was needed — all locators matched.  ║");
        } else {
            for (int i = 0; i < healingEvents.size(); i++) {
                HealingEvent event = healingEvents.get(i);
                log.info("║  Healing #{}", i + 1);
                log.info("║    Original: {}", event.originalLocator());
                log.info("║    Healed:   {}", event.healingResult().healedLocatorExpression());
                log.info("║    Time:     {}ms", event.healingResult().healingDurationMs());
                log.info("║    Tokens:   {}",
                        event.healingResult().tokensUsed() > 0 ? event.healingResult().tokensUsed() : "n/a");
                log.info("║    Provider: {}", event.llmProvider());
                log.info("║    Reason:   {}", event.healingResult().explanation());
                log.info("║");
            }
        }

        log.info("║  Total healings: {}", healingEvents.size());
        log.info("║  Cache hits:     {}", cache.getHits());
        log.info("║  Cache misses:   {}", cache.getMisses());

        // Log cached mappings
        Map<String, HealingResult> entries = cache.getAllEntries();
        if (!entries.isEmpty()) {
            log.info("║");
            log.info("║  Cached locator mappings:");
            entries.forEach((original, result) -> log.info("║    {} → {}", original, result.healedLocatorExpression()));
        }

        log.info("╚══════════════════════════════════════════════════╝");
    }

    @EventListener
    public void onHealingEvent(HealingEvent event) {
        healingEvents.add(event);
        log.info("Healing event captured: {} → {}", event.originalLocator(),
                event.healingResult().healedLocatorExpression());
    }

    public List<HealingEvent> getHealingEvents() {
        return List.copyOf(healingEvents);
    }
}
