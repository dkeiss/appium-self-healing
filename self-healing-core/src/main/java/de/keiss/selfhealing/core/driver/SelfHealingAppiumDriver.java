package de.keiss.selfhealing.core.driver;

import de.keiss.selfhealing.core.healing.HealingOrchestrator;
import de.keiss.selfhealing.core.model.FailureContext;
import de.keiss.selfhealing.core.model.HealingResult;
import io.appium.java_client.AppiumDriver;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Decorator around AppiumDriver that intercepts element-not-found exceptions and delegates to the healing pipeline for
 * automatic repair.
 *
 * The driver wraps both findElement and findElements. On failure: 1. Collects context (page source, screenshot, test
 * code from stack trace) 2. Delegates to HealingOrchestrator (triage → heal → verify) 3. Retries with the healed
 * locator 4. Falls through to original exception if healing fails
 */
@Slf4j
public class SelfHealingAppiumDriver {

    /**
     * Screenshot taken immediately after a successful healing with the red highlight border visible.
     */
    public record HealingScreenshot(String description, byte[] data) {
    }

    @Getter
    private final AppiumDriver delegate;
    @Getter
    private final HealingOrchestrator orchestrator;
    private final SourceCodeResolver sourceCodeResolver;
    private final int maxRetries;
    private final List<HealingScreenshot> healingScreenshots = new ArrayList<>();

    public SelfHealingAppiumDriver(AppiumDriver delegate, HealingOrchestrator orchestrator,
            SourceCodeResolver sourceCodeResolver, int maxRetries) {
        this.delegate = delegate;
        this.orchestrator = orchestrator;
        this.sourceCodeResolver = sourceCodeResolver;
        this.maxRetries = maxRetries;
    }

    public SelfHealingAppiumDriver(AppiumDriver delegate, HealingOrchestrator orchestrator,
            SourceCodeResolver sourceCodeResolver) {
        this(delegate, orchestrator, sourceCodeResolver, 3);
    }

    public WebElement findElement(By locator) {
        try {
            return delegate.findElement(locator);
        } catch (NoSuchElementException | StaleElementReferenceException e) {
            log.warn("Element not found: {} — attempting self-healing", locator);
            return attemptHealAndRetry(locator, e);
        }
    }

    public List<WebElement> findElements(By locator) {
        List<WebElement> elements = delegate.findElements(locator);
        if (elements.isEmpty()) {
            log.warn("No elements found: {} — attempting self-healing", locator);
            try {
                WebElement healed = attemptHealAndRetry(locator,
                        new NoSuchElementException("No elements found: " + locator));
                return List.of(healed);
            } catch (NoSuchElementException e) {
                return List.of();
            }
        }
        return elements;
    }

    /**
     * Explicit wait + find — waits for healing if element isn't immediately present.
     */
    public WebElement findElementWithWait(By locator, int timeoutSeconds) {
        try {
            var wait = new org.openqa.selenium.support.ui.WebDriverWait(delegate,
                    java.time.Duration.ofSeconds(timeoutSeconds));
            return wait.until(d -> d.findElement(locator));
        } catch (TimeoutException | NoSuchElementException e) {
            log.warn("Element not found after {}s wait: {} — attempting self-healing", timeoutSeconds, locator);
            return attemptHealAndRetry(locator,
                    new NoSuchElementException("Timed out waiting for element: " + locator));
        }
    }

    private WebElement attemptHealAndRetry(By failedLocator, WebDriverException originalException) {
        FailureContext context = buildContext(failedLocator, originalException);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            log.info("Healing attempt {}/{} for: {}", attempt, maxRetries, failedLocator);

            HealingResult result = orchestrator.attemptHealing(context);

            if (result.success() && result.healedLocator() != null) {
                try {
                    WebElement element = delegate.findElement(result.healedLocator());
                    log.info("Self-healing SUCCESS: {} → {} (attempt {})", failedLocator, result.healedLocator(),
                            attempt);
                    highlightHealedElement(result.healedLocatorExpression());
                    captureHealingScreenshot(failedLocator, result.healedLocator());
                    return element;
                } catch (NoSuchElementException retryFail) {
                    log.warn("Healed locator also failed: {} — retrying...", result.healedLocator());
                    // Keep original failedLocator, record rejected heal so the next attempt
                    // does not repeat the same non-existent suggestion.
                    context = context.withRejectedLocator(result.healedLocator());
                }
            } else {
                log.warn("Healing returned no usable locator: {}", result.explanation());
                break; // No point retrying if the LLM can't help
            }
        }

        // All attempts failed — rethrow original
        throw originalException;
    }

    private FailureContext buildContext(By failedLocator, WebDriverException exception) {
        String pageSource = safeGetPageSource();
        byte[] screenshot = safeGetScreenshot();
        var callerInfo = sourceCodeResolver.resolveFromStackTrace(Thread.currentThread().getStackTrace());

        return new FailureContext(exception.getMessage(), pageSource, screenshot, failedLocator,
                callerInfo.pageObjectSource(), callerInfo.pageObjectClassName(), callerInfo.stepDefinitionSource(),
                callerInfo.stepName());
    }

    /**
     * Returns and clears all healing screenshots captured since the last call.
     * Called by test hooks (e.g. Cucumber @After) to attach screenshots to reports.
     */
    public List<HealingScreenshot> getAndClearHealingScreenshots() {
        List<HealingScreenshot> copy = List.copyOf(healingScreenshots);
        healingScreenshots.clear();
        return copy;
    }

    /**
     * Captures a screenshot right after the highlight broadcast, so the red border
     * is visible. Waits briefly for the Compose animation to render.
     */
    private void captureHealingScreenshot(By originalLocator, By healedLocator) {
        try {
            Thread.sleep(500); // Wait for Compose highlight animation (300ms tween + buffer)
            byte[] screenshot = ((TakesScreenshot) delegate).getScreenshotAs(OutputType.BYTES);
            String desc = "Self-Healing: " + originalLocator + " → " + healedLocator;
            healingScreenshots.add(new HealingScreenshot(desc, screenshot));
            log.debug("Healing screenshot captured: {}", desc);
        } catch (Exception e) {
            log.debug("Could not capture healing screenshot: {}", e.getMessage());
        }
    }

    /**
     * Sends a broadcast to the Android app to visually highlight the healed element with a red
     * border. Visible in noVNC during demos.
     */
    private void highlightHealedElement(String locatorExpression) {
        if (locatorExpression == null) {
            return;
        }
        try {
            // Extract the raw tag value from expressions like "accessibilityId(fab_search)"
            String tag = locatorExpression.replaceAll(".*\\((.+)\\)", "$1");
            delegate.executeScript("mobile: shell", Map.of(
                    "command", "am broadcast -a de.keiss.selfhealing.HIGHLIGHT --es tag " + tag));
            log.debug("Highlight broadcast sent for tag: {}", tag);
        } catch (Exception e) {
            log.debug("Could not send highlight broadcast: {}", e.getMessage());
        }
    }

    private String safeGetPageSource() {
        try {
            return delegate.getPageSource();
        } catch (Exception e) {
            log.warn("Could not get page source: {}", e.getMessage());
            return null;
        }
    }

    private byte[] safeGetScreenshot() {
        try {
            return ((TakesScreenshot) delegate).getScreenshotAs(OutputType.BYTES);
        } catch (Exception e) {
            log.warn("Could not take screenshot: {}", e.getMessage());
            return null;
        }
    }
}
