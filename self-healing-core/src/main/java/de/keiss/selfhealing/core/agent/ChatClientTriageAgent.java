package de.keiss.selfhealing.core.agent;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.keiss.selfhealing.core.model.FailureContext;
import de.keiss.selfhealing.core.model.TriageResult;
import de.keiss.selfhealing.core.prompt.TriagePromptCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

@Slf4j
@RequiredArgsConstructor
public class ChatClientTriageAgent implements TriageAgent {

    private final ChatClient chatClient;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public TriageResult analyze(FailureContext context) {
        log.info("Triage Agent analyzing failure: {}", firstLine(context.exceptionMessage()));

        var promptCreator = new TriagePromptCreator();

        ChatResponse chatResponse = chatClient.prompt().system(promptCreator.createSystemPrompt())
                .user(promptCreator.createUserPrompt(context)).call().chatResponse();

        logTokenUsage(chatResponse);

        TriageResult result = parseResponse(chatResponse);
        log.info("Triage result: {} (confidence: {})", result.category(), result.confidence());
        return result;
    }

    private TriageResult parseResponse(ChatResponse chatResponse) {
        String content = chatResponse.getResult().getOutput().getText();
        try {
            return MAPPER.readValue(extractJson(content), TriageResult.class);
        } catch (Exception e) {
            log.warn("Failed to parse triage response, defaulting to LOCATOR_CHANGED: {}", e.getMessage());
            return new TriageResult(TriageResult.FailureCategory.LOCATOR_CHANGED, content, 0.5);
        }
    }

    private void logTokenUsage(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return;
        }
        try {
            Usage usage = chatResponse.getMetadata().getUsage();
            if (usage == null) {
                return;
            }
            log.info("Triage token usage — prompt: {}, completion: {}, total: {}", usage.getPromptTokens(),
                    usage.getCompletionTokens(), usage.getTotalTokens());
            logCacheMetrics(usage);
        } catch (Exception e) {
            log.debug("Could not extract triage token usage: {}", e.getMessage());
        }
    }

    private void logCacheMetrics(Usage usage) {
        try {
            Object nativeUsage = resolveNativeUsage(usage);
            if (nativeUsage == null) {
                return;
            }
            Long cacheCreation = invokeOptionalLong(nativeUsage, "cacheCreationInputTokens");
            Long cacheRead = invokeOptionalLong(nativeUsage, "cacheReadInputTokens");
            if (cacheCreation != null || cacheRead != null) {
                log.info("Triage cache — creation: {}, read: {} tokens", cacheCreation != null ? cacheCreation : 0,
                        cacheRead != null ? cacheRead : 0);
            }
        } catch (Exception _) {
            // Native usage is provider-specific; absence is normal for non-Anthropic models
        }
    }

    private Object resolveNativeUsage(Usage usage) {
        try {
            var nativeMethod = usage.getClass().getMethod("getNativeUsage");
            return nativeMethod.invoke(usage);
        } catch (NoSuchMethodException _) {
            return usage;
        } catch (Exception _) {
            return null;
        }
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank())
            return "{}";
        String cleaned = raw.replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("(?s)\\s*```$", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return cleaned;
    }

    private String firstLine(String text) {
        if (text == null)
            return "unknown";
        int idx = text.indexOf('\n');
        return idx > 0 ? text.substring(0, idx) : text;
    }

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
        } catch (Exception _) {
            // reflection failed — method not available on this object
        }
        return null;
    }
}
