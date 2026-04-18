package de.keiss.selfhealing.core.prompt;

import de.keiss.selfhealing.core.model.FailureContext;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the vision-mode toggle on {@link LocatorPromptCreator}: when {@code withVision=true} the user prompt must lead
 * with a "Screenshot" section instructing the LLM to use the attached image; when false (default), no such section
 * appears so non-vision providers don't see misleading guidance.
 */
class LocatorPromptCreatorTest {

    private final LocatorPromptCreator creator = new LocatorPromptCreator();

    @Test
    void createUserPrompt_withVision_includesScreenshotInstructions() {
        var context = new FailureContext("NoSuchElementException", "<hierarchy/>", new byte[]{1, 2, 3},
                By.id("input_from"), "public class SearchPage {}", "SearchPage", null, "search step");

        String prompt = creator.createUserPrompt(context, true);

        assertThat(prompt).startsWith("## Screenshot")
                .contains("PNG screenshot of the current screen is attached")
                .contains("Match labels, icons, and layout positions");
    }

    @Test
    void createUserPrompt_withoutVision_omitsScreenshotSection() {
        var context = new FailureContext("NoSuchElementException", "<hierarchy/>", new byte[]{1, 2, 3},
                By.id("input_from"), "public class SearchPage {}", "SearchPage", null, "search step");

        String prompt = creator.createUserPrompt(context, false);

        assertThat(prompt).doesNotContain("## Screenshot").doesNotContain("attached to this message")
                .startsWith("## Exception");
    }

    @Test
    void createUserPrompt_defaultOverload_isNonVisionMode() {
        var context = new FailureContext("NoSuchElementException", "<hierarchy/>", null, By.id("input_from"),
                "public class SearchPage {}", "SearchPage", null, "search step");

        String prompt = creator.createUserPrompt(context);

        assertThat(prompt).doesNotContain("## Screenshot");
    }
}
