package de.keiss.selfhealing.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "self-healing")
public record SelfHealingProperties(boolean enabled, int maxRetries, String llmProvider, String sourceBasePath,
        Triage triage, Mcp mcp, Vision vision, Cache cache, EnvironmentCheck environmentCheck, BugReports bugReports,
        GitPr gitPr, A2a a2a, Prompt prompt) {

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
        if (a2a == null)
            a2a = A2a.defaults();
        if (prompt == null)
            prompt = Prompt.defaults();
    }

    public record Triage(boolean enabled) {
    }

    public record Mcp(boolean enabled) {
    }

    /**
     * When enabled, the LocatorHealer attaches the failure screenshot (PNG) to the LLM prompt as a multimodal input.
     * Only useful with vision-capable providers (Claude Sonnet, GPT-4o, Qwen3-VL). Default off so non-vision providers
     * (Devstral, GLM-Flash) keep working unchanged.
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

    /**
     * Prompt-construction tuning. The main knob is {@code maxPageSourceChars} — it caps how many characters of the
     * Appium XML page source are included in the heal prompt. The default (15 000) is generous enough for cloud LLMs
     * with large context windows. Local models loaded with a small {@code n_ctx} (e.g. 4 096) will hit a 400 error
     * from LM Studio if the total prompt exceeds the context length. Setting this to ≈ 6 000 keeps the healer prompt
     * well within 4 096 tokens while still exposing all top-level UI elements the LLM needs.
     */
    public record Prompt(int maxPageSourceChars) {

        public Prompt {
            if (maxPageSourceChars <= 0)
                maxPageSourceChars = 15_000;
        }

        public static Prompt defaults() {
            return new Prompt(15_000);
        }
    }

    /**
     * Agent-to-Agent (A2A) integration settings. The {@code server} half exposes healing agents behind an
     * A2A-conformant HTTP/JSON-RPC endpoint (Agent Card at {@code /.well-known/agent.json}, {@code message/send} for
     * invocation). The {@code client} half swaps the in-process {@link de.keiss.selfhealing.core.healing.LocatorHealer}
     * for a remote proxy that calls another process speaking A2A. Both sides are independent — for the first migration
     * spike we typically run both in the same JVM so existing benchmarks exercise the full wire path.
     */
    public record A2a(Server server, Client client) {

        public A2a {
            if (server == null)
                server = Server.defaults();
            if (client == null)
                client = Client.defaults();
        }

        public static A2a defaults() {
            return new A2a(Server.defaults(), Client.defaults());
        }

        public record Server(boolean enabled, String basePath) {

            public Server {
                if (basePath == null || basePath.isBlank())
                    basePath = "/a2a";
            }

            public static Server defaults() {
                return new Server(false, "/a2a");
            }
        }

        public record Client(boolean enabled, String locatorHealerUrl, long requestTimeoutMs) {

            public Client {
                if (requestTimeoutMs <= 0)
                    requestTimeoutMs = 180_000;
            }

            public static Client defaults() {
                return new Client(false, null, 180_000);
            }
        }
    }
}
