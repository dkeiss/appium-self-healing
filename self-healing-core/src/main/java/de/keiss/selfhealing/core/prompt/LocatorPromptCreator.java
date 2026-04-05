package de.keiss.selfhealing.core.prompt;

import de.keiss.selfhealing.core.model.FailureContext;

/**
 * Creates optimized prompts for Appium mobile locator healing. Includes Appium-specific guidance for XML page source
 * analysis.
 */
public class LocatorPromptCreator {

    public String createSystemPrompt() {
        return """
                You are an expert mobile test automation engineer specializing in **Appium + UiAutomator2**.
                A UI element locator has broken because the app's UI was updated to a new version.

                ## Your Task
                1. Analyze the **Appium XML page source** to understand the current UI hierarchy.
                2. Identify which element the broken locator was targeting (by semantic meaning, not just ID).
                3. Create a new working locator for the **same logical element**.
                4. Update the Page Object source code with the corrected locator.

                ## Appium XML Page Source Guide
                The page source is Android UI hierarchy XML. Key attributes:
                - `resource-id`: maps to `By.id("value")` — most reliable. In Jetpack Compose apps,
                  resource-ids are short testTag names (e.g., `fab_search`) without a package prefix.
                - `content-desc`: maps to `AppiumBy.accessibilityId("value")` — very stable
                - `text`: the visible text on the element
                - `class`: Android widget class (e.g., `android.widget.EditText`)
                - `bounds`: screen coordinates `[left,top][right,bottom]`
                - `clickable`, `focusable`, `enabled`: interaction state
                - `index`: child index within parent

                ## Locator Strategy Priority (most to least stable)
                1. **AppiumBy.accessibilityId(contentDesc)** — survives most refactors
                2. **By.id(resourceId)** — stable but IDs change between versions
                3. **AppiumBy.androidUIAutomator("new UiSelector()...")** — flexible compound selectors
                4. **By.xpath(...)** — last resort, fragile

                ## Matching Heuristic
                When the old locator's ID no longer exists, find the replacement by:
                1. **Resource-ID with same semantic root** — e.g., `btn_search` → `fab_search` (both contain "search").
                   Always check ALL resource-ids in the XML for partial keyword matches.
                2. Same `content-desc` describing the same action/purpose
                3. Same widget `class` with `clickable="true"` in a similar position
                4. Same parent hierarchy pattern
                5. Semantic role (input field, button, list, etc.)

                **Important for buttons:** When replacing a button (`btn_*`), the new UI may use a
                FloatingActionButton (`fab_*`) or IconButton instead. Always verify `clickable="true"`.
                Never select static text or labels as button replacements.

                ## Output Format — JSON only, no markdown fences
                {
                  "locatorMethod": "id | xpath | accessibilityId | androidUIAutomator",
                  "locatorValue": "the locator value string",
                  "fixedPageObjectSource": "complete updated Java source of the Page Object",
                  "explanation": "brief explanation: what changed in the UI and how you found the replacement"
                }

                ## Rules
                - Keep the fix **minimal** — only change the broken locator(s).
                - Preserve all method signatures, imports, and class structure.
                - If using `By.id()`, use **only the short resource-id** as it appears in the XML `resource-id` attribute.
                  For Jetpack Compose apps, resource-ids are the `testTag` value without any package prefix
                  (e.g., `fab_search`, NOT `de.keiss.selfhealing.app:id/fab_search`).
                - If the element has a `content-desc`, **always prefer accessibilityId** over id.
                - Never invent locator values — only use attributes visible in the page source.
                """;
    }

    public String createUserPrompt(FailureContext context) {
        var sb = new StringBuilder();

        sb.append("## Exception\n");
        sb.append(firstLine(context.exceptionMessage())).append("\n\n");

        sb.append("## Broken Locator\n");
        sb.append(context.failedLocator().toString()).append("\n\n");

        if (context.pageSourceXml() != null) {
            sb.append("## Current Page Source (Appium XML)\n```xml\n");
            sb.append(smartTruncateXml(context.pageSourceXml(), 15000)).append("\n```\n\n");
        }

        sb.append("## Page Object Class: ").append(context.pageObjectClassName()).append("\n```java\n");
        sb.append(context.pageObjectSource()).append("\n```\n\n");

        if (context.stepDefinitionSource() != null) {
            sb.append("## Step Definition\n```java\n");
            sb.append(context.stepDefinitionSource()).append("\n```\n\n");
        }

        return sb.toString();
    }

    /**
     * Truncate XML intelligently — keep the root structure and trim deep nesting, so the LLM always sees the top-level
     * layout.
     */
    private String smartTruncateXml(String xml, int maxLength) {
        if (xml == null)
            return "";
        if (xml.length() <= maxLength)
            return xml;

        // Keep first 70% and last 15% to preserve both header and footer of the hierarchy
        int headLength = (int) (maxLength * 0.70);
        int tailLength = (int) (maxLength * 0.15);

        return xml.substring(0, headLength) + "\n\n<!-- ... truncated " + (xml.length() - headLength - tailLength)
                + " chars ... -->\n\n" + xml.substring(xml.length() - tailLength);
    }

    private String firstLine(String text) {
        if (text == null)
            return "";
        int idx = text.indexOf('\n');
        return idx > 0 ? text.substring(0, idx) : text;
    }
}
