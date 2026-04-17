package de.keiss.selfhealing.core.healing;

import de.keiss.selfhealing.core.model.FailureContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Uses the Appium MCP Server (via Spring AI MCP Client) to enrich the FailureContext with additional information before
 * healing:
 *
 * - Fresh screenshot (in case the original capture failed) - Current page source (re-fetched via MCP for accuracy) -
 * Element exploration (find similar elements nearby) - App state verification (is the right screen visible?)
 *
 * The MCP server exposes 45+ Appium tools that the LLM can use as function calls to interact with the device.
 *
 * Uses ObjectProvider so the ToolCallbackProvider is resolved lazily — this avoids Spring @ConditionalOnBean ordering
 * problems where the MCP autoconfiguration bean isn't yet registered when user @Configuration classes are parsed.
 */
@Slf4j
@RequiredArgsConstructor
public class McpContextEnricher {

    private final ChatClient chatClient;
    private final ObjectProvider<ToolCallbackProvider> mcpToolProvider;

    /**
     * Enriches the failure context by asking the LLM to gather additional information via MCP tools before attempting
     * healing.
     */
    public FailureContext enrich(FailureContext context) {
        ToolCallbackProvider provider = mcpToolProvider.getIfAvailable();
        if (provider == null) {
            log.warn("MCP enrichment skipped: no ToolCallbackProvider bean available (is spring-ai-starter-mcp-client configured?)");
            return context;
        }
        log.info("Enriching failure context via MCP...");

        try {
            String enrichmentResult = chatClient.prompt().system(ENRICHMENT_SYSTEM_PROMPT)
                    .user(buildEnrichmentPrompt(context)).toolCallbacks(provider).call().content();

            log.info("MCP enrichment complete: {}", firstLine(enrichmentResult));

            // The MCP tools may have gathered fresh page source and screenshot
            // Parse the enrichment result to extract updated context
            return parseEnrichmentResult(context, enrichmentResult);

        } catch (Exception e) {
            log.warn("MCP enrichment failed (continuing with original context): {}", e.getMessage());
            return context;
        }
    }

    private String buildEnrichmentPrompt(FailureContext context) {
        return """
                A test failed with this exception:
                %s

                The broken locator was: %s

                Please gather additional context to help diagnose the issue:
                1. Take a screenshot of the current screen
                2. Get the current page source
                3. Try to find elements similar to the broken locator

                Report what you find. Do NOT attempt to fix the test — just gather context.
                """.formatted(firstLine(context.exceptionMessage()),
                context.failedLocator() != null ? context.failedLocator().toString() : "unknown");
    }

    private FailureContext parseEnrichmentResult(FailureContext original, String enrichmentResult) {
        if (enrichmentResult == null || enrichmentResult.isBlank()) {
            return original;
        }
        String trimmed = enrichmentResult.strip();
        if (trimmed.length() > MAX_ENRICHMENT_CHARS) {
            trimmed = trimmed.substring(0, MAX_ENRICHMENT_CHARS) + "\n...[truncated]";
        }
        return original.withAdditionalContext(trimmed);
    }

    private static final int MAX_ENRICHMENT_CHARS = 4000;

    private String firstLine(String text) {
        if (text == null)
            return "unknown";
        int idx = text.indexOf('\n');
        return idx > 0 ? text.substring(0, idx) : text;
    }

    private static final String ENRICHMENT_SYSTEM_PROMPT = """
            You are a test infrastructure assistant. Your job is to gather diagnostic
            information about a failing mobile test using the available Appium tools.

            You have access to Appium MCP tools. Use them to:
            1. Take a screenshot (appium_screenshot)
            2. Get the page source (appium_get_page_source)
            3. Find a single element on the current screen (appium_find_element)

            Use ONLY the exact tool names listed above. Do NOT invent variants like
            "appium_source" or "appium_find_elements" — they do not exist.
            Report your findings concisely. Do NOT attempt to fix anything.
            """;
}
