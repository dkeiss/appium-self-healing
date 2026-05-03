package de.keiss.selfhealing.core.prompt;

import de.keiss.selfhealing.core.model.FailureContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates optimized prompts for Appium mobile locator healing. Includes Appium-specific guidance for XML page source
 * analysis.
 *
 * <p>
 * The {@code maxPageSourceChars} constructor parameter caps the page-source XML that is embedded in the user prompt.
 * The default (15 000 chars) suits cloud LLMs with large context windows. Set it lower (e.g. 6 000) for local models
 * loaded with a small {@code n_ctx} — LM Studio returns HTTP 400 when the prompt token count exceeds the model's
 * context length.
 */
public class LocatorPromptCreator {

    /** Default: generous limit for cloud LLMs. */
    private static final int DEFAULT_MAX_PAGE_SOURCE_CHARS = 15_000;

    private static final String CODE_FENCE_END = "\n```\n\n";

    private final int maxPageSourceChars;

    public LocatorPromptCreator() {
        this(DEFAULT_MAX_PAGE_SOURCE_CHARS);
    }

    public LocatorPromptCreator(int maxPageSourceChars) {
        // 0 = unlimited (no truncation); negative falls back to default
        this.maxPageSourceChars = maxPageSourceChars >= 0 ? maxPageSourceChars : DEFAULT_MAX_PAGE_SOURCE_CHARS;
    }

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

                ## Locator Collision (only relevant when the user prompt flags it)
                If the user message contains a "## Locator Collision Warning" section, the candidate
                resource-id or content-desc you would normally pick appears on **more than one** XML node.
                In that case `By.id(...)` and `AppiumBy.accessibilityId(...)` will silently match the first
                node — almost always wrong. Use `AppiumBy.androidUIAutomator` with
                `new UiSelector().resourceId("...").instance(N)` (or `.description("...").instance(N)`) and
                choose `N` so it points at the node matching the test's intent.
                - If a screenshot is attached, read the icon glyph / label / position to choose `N`.
                - Otherwise, infer `N` from the layout (toolbar actions are typically leftmost-first).
                When no warning is present, the page source is unambiguous — keep the standard priority.

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
                - If the element has a `content-desc`, **always prefer accessibilityId** over id — unless the
                  Locator Collision section above flags non-uniqueness.
                - Never invent locator values — only use attributes visible in the page source.
                """;
    }

    public String createUserPrompt(FailureContext context) {
        return createUserPrompt(context, false);
    }

    public String createUserPrompt(FailureContext context, boolean withVision) {
        var sb = new StringBuilder();

        // Detect duplicate resource-ids / content-descs once, and only emit the warning + vision
        // disambiguation guidance when the page source actually has collisions. This keeps
        // unambiguous heals (the typical case) on the original priority list and avoids
        // confusing the model into second-guessing simple ID renames.
        Collisions collisions = detectCollisions(context.pageSourceXml());
        boolean hasCollisions = collisions != null && collisions.hasAny();

        if (withVision) {
            sb.append("## Screenshot\n");
            if (hasCollisions) {
                sb.append("A PNG screenshot of the current screen is attached. The XML page source contains\n");
                sb.append("**ambiguous nodes** (see Locator Collision Warning below). Use the screenshot to\n");
                sb.append("disambiguate: identify the test's target element visually (icon glyph / label /\n");
                sb.append("position), then map it to the matching `instance(N)` in the XML.\n\n");
            } else {
                sb.append("A PNG screenshot of the current screen is attached as a secondary signal. The\n");
                sb.append("XML page source is unambiguous for this heal, so a standard id- or\n");
                sb.append("accessibilityId-based locator is fine; the screenshot is just for sanity-checking.\n\n");
            }
        }

        sb.append("## Exception\n");
        sb.append(firstLine(context.exceptionMessage())).append("\n\n");

        sb.append("## Broken Locator\n");
        sb.append(context.failedLocator().toString()).append("\n\n");

        if (context.rejectedLocators() != null && !context.rejectedLocators().isEmpty()) {
            sb.append("## Already Tried — DO NOT Suggest Again\n");
            sb.append("The following locators were proposed in previous attempts but could NOT be found in the UI.\n");
            sb.append("They are either invented or no longer exist. Suggest something different that appears\n");
            sb.append("**verbatim** in the XML page source below:\n");
            for (var rejected : context.rejectedLocators()) {
                sb.append("- `").append(rejected.toString()).append("`\n");
            }
            sb.append("\n");
        }

        if (context.pageSourceXml() != null) {
            sb.append("## Current Page Source (Appium XML)\n```xml\n");
            sb.append(smartTruncateXml(context.pageSourceXml(), this.maxPageSourceChars)).append(CODE_FENCE_END);
        }

        if (hasCollisions) {
            sb.append("## Locator Collision Warning\n");
            sb.append("The following attribute values appear on **multiple** XML nodes — `By.id(...)` or\n");
            sb.append("`AppiumBy.accessibilityId(...)` will silently match only the first one. For any of\n");
            sb.append("these, return `androidUIAutomator` with `.instance(N)` instead, choosing `N` from the\n");
            sb.append("layout / screenshot per the system-prompt rule.\n\n");
            if (!collisions.duplicateResourceIds.isEmpty()) {
                sb.append("Duplicate `resource-id` values:\n");
                collisions.duplicateResourceIds.forEach((value, count) -> sb.append("- `").append(value)
                        .append("` — appears on ").append(count).append(" nodes\n"));
                sb.append('\n');
            }
            if (!collisions.duplicateContentDescs.isEmpty()) {
                sb.append("Duplicate `content-desc` values:\n");
                collisions.duplicateContentDescs.forEach((value, count) -> sb.append("- `").append(value)
                        .append("` — appears on ").append(count).append(" nodes\n"));
                sb.append('\n');
            }
        }

        sb.append("## Page Object Class: ").append(context.pageObjectClassName()).append("\n```java\n");
        sb.append(context.pageObjectSource()).append(CODE_FENCE_END);

        if (context.stepDefinitionSource() != null) {
            sb.append("## Step Definition\n```java\n");
            sb.append(context.stepDefinitionSource()).append(CODE_FENCE_END);
        }

        if (context.additionalContext() != null && !context.additionalContext().isBlank()) {
            sb.append("## Diagnostic Notes from MCP Enrichment\n");
            sb.append(context.additionalContext()).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * Truncate XML intelligently — keep the root structure and trim deep nesting, so the LLM always sees the top-level
     * layout. Pass {@code maxLength = 0} to disable truncation entirely.
     */
    private String smartTruncateXml(String xml, int maxLength) {
        if (xml == null)
            return "";
        if (maxLength <= 0 || xml.length() <= maxLength)
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

    private static final Pattern RESOURCE_ID_PATTERN = Pattern.compile("resource-id=\"([^\"]+)\"");
    private static final Pattern CONTENT_DESC_PATTERN = Pattern.compile("content-desc=\"([^\"]+)\"");

    /**
     * Scans the Appium XML page source for `resource-id` and `content-desc` values that appear on more than one node.
     * Empty values are ignored — Appium emits empty content-desc for many nodes, which is not a real collision.
     */
    Collisions detectCollisions(String xml) {
        if (xml == null || xml.isBlank())
            return null;
        Map<String, Integer> resourceIdCounts = countMatches(xml, RESOURCE_ID_PATTERN);
        Map<String, Integer> contentDescCounts = countMatches(xml, CONTENT_DESC_PATTERN);
        Map<String, Integer> duplicateResourceIds = filterDuplicates(resourceIdCounts);
        Map<String, Integer> duplicateContentDescs = filterDuplicates(contentDescCounts);
        return new Collisions(duplicateResourceIds, duplicateContentDescs);
    }

    private Map<String, Integer> countMatches(String xml, Pattern pattern) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Matcher matcher = pattern.matcher(xml);
        while (matcher.find()) {
            String value = matcher.group(1);
            if (value == null || value.isEmpty())
                continue;
            counts.merge(value, 1, Integer::sum);
        }
        return counts;
    }

    private Map<String, Integer> filterDuplicates(Map<String, Integer> counts) {
        Map<String, Integer> duplicates = new LinkedHashMap<>();
        counts.forEach((value, count) -> {
            if (count > 1)
                duplicates.put(value, count);
        });
        return duplicates;
    }

    record Collisions(Map<String, Integer> duplicateResourceIds, Map<String, Integer> duplicateContentDescs) {
        boolean hasAny() {
            return !duplicateResourceIds.isEmpty() || !duplicateContentDescs.isEmpty();
        }
    }
}
