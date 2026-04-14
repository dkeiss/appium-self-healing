package de.keiss.selfhealing.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "self-healing")
public record SelfHealingProperties(boolean enabled, int maxRetries, String llmProvider, String sourceBasePath,
        Triage triage, EnvironmentCheck environmentCheck, BugReports bugReports, GitPr gitPr) {

    public SelfHealingProperties {
        if (maxRetries <= 0)
            maxRetries = 3;
        if (llmProvider == null)
            llmProvider = "anthropic";
        if (triage == null)
            triage = new Triage(true);
        if (environmentCheck == null)
            environmentCheck = EnvironmentCheck.defaults();
        if (bugReports == null)
            bugReports = BugReports.defaults();
        if (gitPr == null)
            gitPr = GitPr.defaults();
    }

    public record Triage(boolean enabled) {
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
     * {@code self-healing.git-pr.enabled=true}.
     */
    public record GitPr(boolean enabled, String remoteName, String baseBranch, String branchPrefix, String githubToken,
            String githubRepoOwner, String githubRepoName) {

        public GitPr {
            if (remoteName == null || remoteName.isBlank())
                remoteName = "origin";
            if (baseBranch == null || baseBranch.isBlank())
                baseBranch = "main";
            if (branchPrefix == null || branchPrefix.isBlank())
                branchPrefix = "fix/self-healing-";
        }

        public static GitPr defaults() {
            return new GitPr(false, "origin", "main", "fix/self-healing-", null, null, null);
        }
    }
}
