package de.keiss.selfhealing.core.healing;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.keiss.selfhealing.core.config.SelfHealingProperties;
import de.keiss.selfhealing.core.model.FailureContext;
import de.keiss.selfhealing.core.model.HealingResult;
import de.keiss.selfhealing.core.prompt.LocatorPromptCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

/**
 * Default in-process {@link LocatorHealer} that calls an LLM directly through Spring AI's {@link ChatClient}. When A2A
 * client mode is enabled, the orchestrator does not inject this bean directly — it talks to a remote A2A agent instead,
 * and this class only powers the local A2A server endpoint that the remote caller eventually reaches.
 */
@Slf4j
@RequiredArgsConstructor
public class ChatClientLocatorHealer implements LocatorHealer {

    private final ChatClient chatClient;
    private final SelfHealingProperties properties;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public HealingResult heal(FailureContext context) {
        long startTime = System.currentTimeMillis();
        log.info("LocatorHealer attempting to heal: {}", context.failedLocator());

        try {
            int maxPageSourceChars = properties.prompt().maxPageSourceChars();
            var promptCreator = new LocatorPromptCreator(maxPageSourceChars);
            boolean useVision = properties.vision().enabled() && context.screenshot() != null
                    && context.screenshot().length > 0;
            String userPrompt = promptCreator.createUserPrompt(context, useVision);

            var requestSpec = chatClient.prompt().system(promptCreator.createSystemPrompt());
            if (useVision) {
                Media screenshot = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(context.screenshot()).build();
                log.info("Vision mode: attaching {} byte screenshot to heal prompt", context.screenshot().length);
                requestSpec = requestSpec.user(spec -> spec.text(userPrompt).media(screenshot));
            } else {
                requestSpec = requestSpec.user(userPrompt);
            }

            ChatResponse chatResponse = requestSpec.call().chatResponse();

            // Extract token usage and cache metrics
            int totalTokens = logTokenUsage(chatResponse);

            // Parse entity from response content — extract JSON robustly
            String content = chatResponse.getResult().getOutput().getText();
            LocatorResponse response = MAPPER.readValue(extractJson(content), LocatorResponse.class);

            if (response == null || response.locatorMethod() == null) {
                return HealingResult.failed("LLM returned no valid locator", elapsed(startTime));
            }

            By healedLocator = LocatorFactory.construct(response.locatorMethod(), response.locatorValue());
            long duration = elapsed(startTime);

            log.info("Healed locator: {} -> {} ({}ms)", context.failedLocator(), healedLocator, duration);

            return new HealingResult(true, healedLocator,
                    response.locatorMethod() + "(\"" + response.locatorValue() + "\")",
                    response.fixedPageObjectSource(), response.explanation(), duration, totalTokens);
        } catch (Exception e) {
            log.error("Healing failed", e);
            return HealingResult.failed(e.getMessage(), elapsed(startTime));
        }
    }

    /**
     * Extracts JSON from LLM response that may contain leading/trailing text or markdown fences.
     */
    private String extractJson(String raw) {
        if (raw == null || raw.isBlank())
            return "{}";
        // Strip markdown fences
        String cleaned = raw.replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("(?s)\\s*```$", "").trim();
        // Find the first '{' and last '}' to extract the JSON object
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return cleaned;
    }

    /**
     * Extracts and logs token usage from the ChatResponse, including Anthropic prompt cache metrics.
     * <p>
     * Spring AI wraps the native Anthropic SDK Usage object. Cache metrics (cacheCreationInputTokens,
     * cacheReadInputTokens) are available via the native usage and are extracted using reflection since the Spring AI
     * Usage interface does not expose them directly.
     */
    private int logTokenUsage(ChatResponse chatResponse) {
        try {
            ChatResponseMetadata metadata = chatResponse.getMetadata();
            if (metadata == null)
                return 0;

            Usage usage = metadata.getUsage();
            if (usage == null)
                return 0;

            long promptTokens = usage.getPromptTokens();
            long completionTokens = usage.getCompletionTokens();
            long totalTokens = usage.getTotalTokens();

            // Log basic usage
            log.info("Token usage — prompt: {}, completion: {}, total: {}", promptTokens, completionTokens,
                    totalTokens);

            // Extract Anthropic cache metrics from the native Usage object via reflection.
            // The Anthropic SDK Usage has cacheCreationInputTokens() and cacheReadInputTokens()
            // that return Optional<Long>.
            extractAnthropicCacheMetrics(usage);

            return (int) totalTokens;
        } catch (Exception e) {
            log.debug("Could not extract token usage: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Attempts to extract Anthropic-specific cache metrics from the Usage object. Uses reflection to access the native
     * Anthropic SDK Usage wrapped by Spring AI.
     */
    private void extractAnthropicCacheMetrics(Usage usage) {
        try {
            // Spring AI DefaultUsage wraps the native usage. Try to get it via getNativeUsage()
            Object nativeUsage = null;
            try {
                var nativeMethod = usage.getClass().getMethod("getNativeUsage");
                nativeUsage = nativeMethod.invoke(usage);
            } catch (NoSuchMethodException e) {
                // Not a DefaultUsage wrapper — try the usage object itself
                nativeUsage = usage;
            }

            if (nativeUsage == null)
                return;

            // Try to call cacheCreationInputTokens() and cacheReadInputTokens() on the native object
            Long cacheCreation = invokeOptionalLong(nativeUsage, "cacheCreationInputTokens");
            Long cacheRead = invokeOptionalLong(nativeUsage, "cacheReadInputTokens");

            if (cacheCreation != null || cacheRead != null) {
                log.info("Anthropic cache — creation: {}, read: {} tokens", cacheCreation != null ? cacheCreation : 0,
                        cacheRead != null ? cacheRead : 0);
            }
        } catch (Exception e) {
            log.debug("Could not extract Anthropic cache metrics: {}", e.getMessage());
        }
    }

    /**
     * Invokes a method that returns Optional&lt;Long&gt; on the given object, returning the value or null.
     */
    @SuppressWarnings("unchecked")
    private Long invokeOptionalLong(Object obj, String methodName) {
        try {
            var method = obj.getClass().getMethod(methodName);
            Object result = method.invoke(obj);
            if (result instanceof java.util.Optional<?> opt) {
                return (Long) opt.orElse(null);
            } else if (result instanceof Long l) {
                return l;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private long elapsed(long startTime) {
        return System.currentTimeMillis() - startTime;
    }

    private record LocatorResponse(String locatorMethod, String locatorValue, String fixedPageObjectSource,
            String explanation) {
    }
}
