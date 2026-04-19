package de.keiss.selfhealing.core.git;

import de.keiss.selfhealing.core.config.SelfHealingProperties;
import de.keiss.selfhealing.core.model.HealingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.IOException;

/**
 * Creates Pull Requests on GitHub with structured healing context in the body.
 */
@Slf4j
@RequiredArgsConstructor
public class GitHubPrService {

    private final SelfHealingProperties.GitPr config;

    /**
     * Creates a Pull Request on GitHub for a healed locator fix.
     *
     * @param branchName
     *            the branch containing the fix
     * @param pageObjectClassName
     *            simple class name (e.g. "SearchPage")
     * @param originalLocator
     *            the original broken locator string
     * @param healingResult
     *            the healing result with fix details
     * @param scenarioName
     *            the test scenario that triggered the healing
     * @param llmProvider
     *            the LLM provider used for healing
     * @return the HTML URL of the created PR
     */
    public String createPullRequest(String branchName, String pageObjectClassName, String originalLocator,
            HealingResult healingResult, String scenarioName, String llmProvider) throws IOException {
        String title = "fix: Self-Healing " + pageObjectClassName;
        String body = buildPrBody(pageObjectClassName, originalLocator, healingResult, scenarioName, llmProvider);

        if (config.dryRun()) {
            String repoFullName = config.githubRepoOwner() + "/" + config.githubRepoName();
            String dryRunUrl = "dry-run://" + repoFullName + "/pulls?head=" + branchName + "&base="
                    + config.baseBranch();
            log.info("[DRY-RUN] Would open PR on {}: {} ({} → {})\nTitle: {}\nBody:\n{}", repoFullName, dryRunUrl,
                    branchName, config.baseBranch(), title, body);
            return dryRunUrl;
        }

        GitHub github = new GitHubBuilder().withOAuthToken(config.githubToken()).build();
        String repoFullName = config.githubRepoOwner() + "/" + config.githubRepoName();
        GHRepository repo = github.getRepository(repoFullName);

        var pr = repo.createPullRequest(title, branchName, config.baseBranch(), body);

        String prUrl = pr.getHtmlUrl().toString();
        log.info("Created PR #{} for {}: {}", pr.getNumber(), pageObjectClassName, prUrl);
        return prUrl;
    }

    String buildPrBody(String pageObjectClassName, String originalLocator, HealingResult healingResult,
            String scenarioName, String llmProvider) {
        var sb = new StringBuilder();
        sb.append("## Self-Healing: Auto-Fix for ").append(pageObjectClassName).append("\n\n");

        sb.append("**Category:** LOCATOR_CHANGED\n");
        sb.append("**LLM Provider:** ").append(llmProvider).append("\n");
        sb.append("**Scenario:** ").append(scenarioName != null ? scenarioName : "unknown").append("\n\n");

        sb.append("### Change\n");
        sb.append("- **Original locator:** `").append(originalLocator).append("`\n");
        if (healingResult.healedLocatorExpression() != null) {
            sb.append("- **Healed locator:** `").append(healingResult.healedLocatorExpression()).append("`\n");
        }
        sb.append("\n");

        if (healingResult.explanation() != null) {
            sb.append("### Explanation\n");
            sb.append(healingResult.explanation()).append("\n\n");
        }

        sb.append("---\n");
        sb.append("*This PR was created automatically by the Self-Healing Agent. ");
        sb.append("Please review the change and run the tests manually before merging.*\n");

        return sb.toString();
    }
}
