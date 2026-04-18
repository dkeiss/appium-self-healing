package de.keiss.selfhealing.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "self-healing")
public record SelfHealingProperties(boolean enabled, int maxRetries, String llmProvider, String sourceBasePath,
        Triage triage, Mcp mcp, Vision vision, Cache cache, EnvironmentCheck environmentCheck, BugReports bugReports,
        GitPr gitPr) {

    public SelfHealingProperties {
        if (maxRetries <= 0)
            maxRetries = 3;
        if (llmProvider == null)
            llmProvider = "anthropic";
        if (triage == null)
            triage = new Triage(true);
        if (mcp == null)
            mcp = new Mcp(false);
        if (vision == null)
            vision = new Vision(false);
        if (cache == null)
            cache = Cache.defaults();
        if (environmentCheck == null)
            environmentCheck = EnvironmentCheck.defaults();
        if (bugReports == null)
            bugReports = BugReports.defaults();
        if (gitPr == null)
            gitPr = GitPr.defaults();
    }

    public record Triage(boolean enabled) {
    }

    public record Mcp(boolean enabled) {
    }

    /**
     * When enabled, the LocatorHealer attaches the failure screenshot (PNG) to the LLM prompt as a multimodal
     * input. Only useful with vision-capable providers (Claude Sonnet, GPT-4o, Qwen3-VL). Default off so non-vision
     * providers (Devstral, GLM-Flash) keep working unchanged.
     */
    public record Vision(boolean enabled) {
    }

    /**
     * Configuration for the in-memory locator healing cache. Disabling is useful for LLM benchmark runs where a single
     * wrong heal in an early scenario would otherwise be reused (via cache hit) and cause every subsequent scenario to
     * fail at the same step — masking the LLM's ability to heal the remaining locators.
     */
    public record Cache(boolean enabled) {

        public static Cache defaults() {
            return new Cache(true);
        }
    }

    public record EnvironmentCheck(boolean enabled, String backendUrl, String appiumUrl, long connectTimeoutMs,
            long requestTimeoutMs, long retryBackoffMs) {

        public EnvironmentCheck {
            if (connectTimeoutMs <= 0)
                connectTimeoutMs = 2000;
            if (requestTimeoutMs <= 0)
                requestTimeoutMs = 3000;
            if (retryBackoffMs <= 0)
                retryBackoffMs = 2000;
        }

        public static EnvironmentCheck defaults() {
            return new EnvironmentCheck(true, null, null, 2000, 3000, 2000);
        }
    }

    public record BugReports(boolean enabled, boolean persistJson, String outputPath) {

        public BugReports {
            if (outputPath == null || outputPath.isBlank())
                outputPath = "build/reports/bugs";
        }

        public static BugReports defaults() {
            return new BugReports(true, true, "build/reports/bugs");
        }
    }

    /**
     * Configuration for automatic PR creation when a locator is healed. Disabled by default — opt-in via
     * {@code self-healing.git-pr.enabled=true}. When {@code dryRun=true}, the pipeline logs the full PR plan (branch,
     * commit, body) without touching git or calling the GitHub API.
     */
    public record GitPr(boolean enabled, boolean dryRun, String remoteName, String baseBranch, String branchPrefix,
            String githubToken, String githubRepoOwner, String githubRepoName) {

        public GitPr {
            if (remoteName == null || remoteName.isBlank())
                remoteName = "origin";
            if (baseBranch == null || baseBranch.isBlank())
                baseBranch = "main";
            if (branchPrefix == null || branchPrefix.isBlank())
                branchPrefix = "fix/self-healing-";
        }

        public static GitPr defaults() {
            return new GitPr(false, false, "origin", "main", "fix/self-healing-", null, null, null);
        }
    }
}
