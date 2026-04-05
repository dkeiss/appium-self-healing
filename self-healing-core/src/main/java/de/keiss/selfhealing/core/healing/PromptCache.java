package de.keiss.selfhealing.core.healing;

import de.keiss.selfhealing.core.model.HealingResult;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches healing results by locator string to avoid repeated LLM calls for the same broken locator within a single test
 * run.
 *
 * This is critical for performance: if the same broken locator is used multiple times in different scenarios (e.g.,
 * shared Page Object), we only call the LLM once.
 */
@Slf4j
public class PromptCache {

    private final Map<String, HealingResult> cache = new ConcurrentHashMap<>();
    @Getter
    private int hits = 0;
    @Getter
    private int misses = 0;

    public HealingResult get(String locatorKey) {
        HealingResult cached = cache.get(locatorKey);
        if (cached != null) {
            hits++;
            log.debug("Cache HIT for locator: {} (hits: {}, misses: {})", locatorKey, hits, misses);
        } else {
            misses++;
            log.debug("Cache MISS for locator: {} (hits: {}, misses: {})", locatorKey, hits, misses);
        }
        return cached;
    }

    public void put(String locatorKey, HealingResult result) {
        if (result.success()) {
            cache.put(locatorKey, result);
            log.debug("Cached healing for: {} → {}", locatorKey, result.healedLocatorExpression());
        }
    }

    public void clear() {
        cache.clear();
        hits = 0;
        misses = 0;
    }

    public int size() {
        return cache.size();
    }

    public Map<String, HealingResult> getAllEntries() {
        return Map.copyOf(cache);
    }
}
