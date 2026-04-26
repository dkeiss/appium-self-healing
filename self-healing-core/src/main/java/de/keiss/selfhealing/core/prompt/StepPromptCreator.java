package de.keiss.selfhealing.core.prompt;

import de.keiss.selfhealing.core.model.FailureContext;

/**
 * Creates prompts for step-level healing — when the test logic itself needs adjustment (e.g., navigation flow changed,
 * new intermediate steps needed).
 */
public class StepPromptCreator {

    private static final String CODE_FENCE_END = "\n```\n\n";

    public String createSystemPrompt() {
        return """
                You are an expert mobile test automation engineer fixing a **Cucumber step definition**
                that fails due to UI flow changes in the app.

                ## Context
                The app was updated and the test flow no longer matches:
                - A new intermediate screen may have been added
                - The navigation sequence may have changed
                - An element interaction may need different handling (e.g., click → long press)
                - A wait/synchronization issue may exist

                ## Your Task
                1. Analyze the exception, step definition code, and page source.
                2. Determine what changed in the app's flow.
                3. Provide a corrected version of the step definition method.

                ## Output Format — JSON only, no markdown fences
                {
                  "fixedMethodSource": "the complete corrected Java method (just the method, not the class)",
                  "fixedPageObjectSource": "if the Page Object also needs changes, the complete updated source; otherwise null",
                  "explanation": "what changed in the flow and how the fix addresses it",
                  "requiresNewStep": false,
                  "newStepSuggestion": "if a new Cucumber step is needed, suggest the Gherkin line; otherwise null"
                }

                ## Rules
                - Keep changes minimal — only fix what's broken.
                - Preserve Cucumber annotations and parameter bindings.
                - If a wait is needed, use explicit waits (WebDriverWait), not Thread.sleep.
                - If navigation changed, update the Page Object navigation methods.
                """;
    }

    public String createUserPrompt(FailureContext context) {
        var sb = new StringBuilder();

        sb.append("## Exception\n");
        sb.append(context.exceptionMessage()).append("\n\n");

        sb.append("## Failed Step: ").append(context.stepName()).append("\n\n");

        if (context.stepDefinitionSource() != null) {
            sb.append("## Step Definition Source\n```java\n");
            sb.append(context.stepDefinitionSource()).append(CODE_FENCE_END);
        }

        if (context.pageObjectSource() != null) {
            sb.append("## Page Object: ").append(context.pageObjectClassName()).append("\n```java\n");
            sb.append(context.pageObjectSource()).append(CODE_FENCE_END);
        }

        if (context.pageSourceXml() != null) {
            sb.append("## Current Page Source (Appium XML)\n```xml\n");
            sb.append(truncate(context.pageSourceXml(), 10000)).append(CODE_FENCE_END);
        }

        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null)
            return "";
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength) + "\n... [truncated]";
    }
}
