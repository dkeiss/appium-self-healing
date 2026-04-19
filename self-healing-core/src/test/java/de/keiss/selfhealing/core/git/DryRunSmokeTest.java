package de.keiss.selfhealing.core.git;

import de.keiss.selfhealing.core.config.SelfHealingProperties;
import de.keiss.selfhealing.core.model.HealingEvent;
import de.keiss.selfhealing.core.model.HealingResult;
import de.keiss.selfhealing.core.model.TriageResult.FailureCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end smoke test for dry-run mode: wires real GitService + GitHubPrService (no mocks) with dry-run enabled,
 * fires a HealingEvent through AutoFixPrCreator, and verifies the dry-run log output is produced without any git or
 * GitHub API interactions. Redirects stdout so the dry-run log lines can be asserted.
 */
class DryRunSmokeTest {

    @Test
    void fullFlow_dryRun_logsPlanWithoutSideEffects(@TempDir Path tempDir) {
        var config = new SelfHealingProperties.GitPr(true, true, "origin", "self-healing-playground",
                "fix/self-healing-", "", "dkeiss", "appium-self-healing");
        var gitService = new GitService(tempDir, config); // tempDir is not even a git repo — dry-run never opens it
        var gitHubPrService = new GitHubPrService(config);
        ApplicationEventPublisher noopPublisher = event -> {
        };
        var creator = new AutoFixPrCreator(gitService, gitHubPrService, noopPublisher);

        var healingResult = new HealingResult(true, null, "By.id(\"departure_station\")",
                "package de.keiss.selfhealing.tests.pages;\n\n" + "public class SearchPage {\n"
                        + "    private static final By INPUT_FROM = By.id(\"departure_station\");\n" + "}\n",
                "The locator was renamed from input_from to departure_station in the new app version.", 1200, 850);
        var event = new HealingEvent(Instant.now(), "der Nutzer sucht eine Verbindung", FailureCategory.LOCATOR_CHANGED,
                healingResult, "By.id(\"input_from\")", "anthropic", "SearchPage",
                "de/keiss/selfhealing/tests/pages/SearchPage.java");

        var captured = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(captured, true));
        try {
            creator.onHealingEvent(event);
        } finally {
            System.setOut(originalOut);
        }

        String output = captured.toString();
        System.out.println("──────────── Captured dry-run output ────────────");
        System.out.println(output);
        System.out.println("──────────────────────────────────────────────────");

        assertThat(output).contains("[DRY-RUN] Would create branch 'fix/self-healing-SearchPage-")
                .contains("from 'self-healing-playground'")
                .contains("src/test/java/de/keiss/selfhealing/tests/pages/SearchPage.java")
                .contains("fix(self-healing): update SearchPage locators")
                .contains("[DRY-RUN] Would open PR on dkeiss/appium-self-healing")
                .contains("dry-run://dkeiss/appium-self-healing/pulls?head=fix/self-healing-SearchPage-")
                .contains("base=self-healing-playground").contains("Title: fix: Self-Healing SearchPage")
                .contains("## Self-Healing: Auto-Fix for SearchPage")
                .contains("**Original locator:** `By.id(\"input_from\")`")
                .contains("**Healed locator:** `By.id(\"departure_station\")`");
    }
}
