package de.keiss.selfhealing.core.prompt;

import de.keiss.selfhealing.core.model.FailureContext;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the vision-mode toggle and locator-collision detection on {@link LocatorPromptCreator}:
 * - when {@code withVision=true}, the user prompt leads with a "## Screenshot" section
 * - when the page source has duplicate `resource-id` or `content-desc` values, a "## Locator Collision Warning"
 *   section is appended and the screenshot block (if present) shifts to disambiguation phrasing
 * - when neither condition applies, the prompt is the unambiguous baseline so simple ID renames are not disturbed
 */
class LocatorPromptCreatorTest {

    private final LocatorPromptCreator creator = new LocatorPromptCreator();

    @Test
    void createUserPrompt_withVision_includesScreenshotInstructions() {
        var context = new FailureContext("NoSuchElementException", "<hierarchy/>", new byte[]{1, 2, 3},
                By.id("input_from"), "public class SearchPage {}", "SearchPage", null, "search step");

        String prompt = creator.createUserPrompt(context, true);

        assertThat(prompt).startsWith("## Screenshot").contains("PNG screenshot of the current screen is attached");
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

    @Test
    void createUserPrompt_withDuplicateResourceIds_emitsCollisionWarning() {
        String xml = """
                <hierarchy>
                  <node resource-id="toolbar_action" content-desc="Aktion"/>
                  <node resource-id="toolbar_action" content-desc="Aktion"/>
                  <node resource-id="toolbar_action" content-desc="Aktion"/>
                  <node resource-id="results_container"/>
                </hierarchy>
                """;
        var context = new FailureContext("NoSuchElementException", xml, null, By.id("btn_m3n"),
                "public class ResultPage {}", "ResultPage", null, null);

        String prompt = creator.createUserPrompt(context);

        assertThat(prompt).contains("## Locator Collision Warning")
                .contains("`toolbar_action` — appears on 3 nodes")
                .contains("`Aktion` — appears on 3 nodes")
                .contains("androidUIAutomator");
    }

    @Test
    void createUserPrompt_withUniqueAttributes_omitsCollisionWarning() {
        String xml = """
                <hierarchy>
                  <node resource-id="departure_station"/>
                  <node resource-id="arrival_station"/>
                  <node resource-id="fab_search"/>
                </hierarchy>
                """;
        var context = new FailureContext("NoSuchElementException", xml, null, By.id("input_from"),
                "public class SearchPage {}", "SearchPage", null, null);

        String prompt = creator.createUserPrompt(context);

        assertThat(prompt).doesNotContain("## Locator Collision Warning");
    }

    @Test
    void createUserPrompt_visionWithCollision_promptsForDisambiguation() {
        String xml = """
                <hierarchy>
                  <node resource-id="toolbar_action"/>
                  <node resource-id="toolbar_action"/>
                  <node resource-id="toolbar_action"/>
                </hierarchy>
                """;
        var context = new FailureContext("NoSuchElementException", xml, new byte[]{1, 2, 3}, By.id("btn_m3n"),
                "public class ResultPage {}", "ResultPage", null, null);

        String prompt = creator.createUserPrompt(context, true);

        assertThat(prompt).startsWith("## Screenshot").contains("ambiguous nodes").contains("instance(N)")
                .contains("## Locator Collision Warning");
    }

    @Test
    void detectCollisions_ignoresEmptyContentDesc() {
        // Appium emits content-desc="" for many nodes; that's not a real collision.
        String xml = """
                <hierarchy>
                  <node resource-id="a" content-desc=""/>
                  <node resource-id="b" content-desc=""/>
                </hierarchy>
                """;

        var collisions = creator.detectCollisions(xml);

        assertThat(collisions.duplicateContentDescs()).isEmpty();
    }
}
