package de.keiss.selfhealing.core.prompt;

import de.keiss.selfhealing.core.model.FailureContext;

/**
 * Creates prompts for the Triage Agent that classifies test failures into actionable categories before attempting any
 * healing.
 */
public class TriagePromptCreator {

    public String createSystemPrompt() {
        return """
                You are an expert QA automation engineer analyzing a **mobile Appium test failure**.
                Your task is to classify the root cause into exactly one category.

                ## Categories

                ### LOCATOR_CHANGED
                An element locator (ID, XPath, accessibility ID) no longer matches the current UI.
                **Indicators:**
                - `NoSuchElementException` with a `By.id`, `By.xpath`, or `accessibilityId` reference
                - `StaleElementReferenceException`
                - The locator string contains an ID/path that doesn't appear in the page source
                - The page source shows the UI loaded correctly but element IDs differ

                ### TEST_LOGIC_ERROR
                The test logic itself is wrong — sequence, assertion, or data mismatch.
                **Indicators:**
                - `AssertionError` / `ComparisonFailure` — element was found but value is wrong
                - Wrong navigation sequence (test expects screen A but is on screen B)
                - Test data doesn't match expected output

                ### ENVIRONMENT_ISSUE
                The test infrastructure has a problem, not the test or app.
                **Indicators:**
                - `WebDriverException` about session not found / connection refused
                - `TimeoutException` during session creation or server communication
                - `java.net.ConnectException` — server unreachable
                - Emulator/device not responding
                - Page source is empty or shows a system error screen

                ### APP_BUG
                The application has a functional bug — test and locators are correct.
                **Indicators:**
                - Element found with correct locator but displays wrong data
                - App shows an unexpected error/crash screen
                - Backend returns error (visible in UI)
                - Element is found but not interactable (`ElementNotInteractableException` with correct locator)

                ## Output Format — JSON only, no markdown fences
                {
                  "category": "LOCATOR_CHANGED | TEST_LOGIC_ERROR | ENVIRONMENT_ISSUE | APP_BUG",
                  "reasoning": "1-3 sentences explaining your classification",
                  "confidence": 0.0 to 1.0
                }

                ## Decision Rules
                - If the exception is `NoSuchElementException` and the page source shows a loaded UI → **LOCATOR_CHANGED** (most common case)
                - If the exception is `NoSuchElementException` and the page source is empty/error → **ENVIRONMENT_ISSUE**
                - If an assertion fails but elements were found → **APP_BUG** or **TEST_LOGIC_ERROR**
                - When in doubt between LOCATOR_CHANGED and APP_BUG, prefer LOCATOR_CHANGED (it's healable)
                """;
    }

    public String createUserPrompt(FailureContext context) {
        var sb = new StringBuilder();
        sb.append("## Exception\n");
        sb.append(context.exceptionMessage()).append("\n\n");

        if (context.failedLocator() != null) {
            sb.append("## Failed Locator\n");
            sb.append(context.failedLocator().toString()).append("\n\n");
        }

        if (context.pageSourceXml() != null) {
            sb.append("## Page Source Available: YES (").append(context.pageSourceXml().length()).append(" chars)\n");
            // Only include a summary for triage — full source goes to the healer
            sb.append("First 2000 chars:\n```xml\n");
            sb.append(context.pageSourceXml(), 0, Math.min(2000, context.pageSourceXml().length()));
            sb.append("\n```\n\n");
        } else {
            sb.append("## Page Source Available: NO\n\n");
        }

        if (context.screenshot() != null) {
            sb.append("## Screenshot Available: YES (").append(context.screenshot().length).append(" bytes)\n\n");
        }

        if (context.pageObjectSource() != null) {
            sb.append("## Page Object Source\n```java\n");
            sb.append(context.pageObjectSource()).append("\n```\n\n");
        }

        if (context.stepDefinitionSource() != null) {
            sb.append("## Step Definition Source\n```java\n");
            sb.append(context.stepDefinitionSource()).append("\n```\n\n");
        }

        return sb.toString();
    }
}
