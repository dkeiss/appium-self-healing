package de.keiss.selfhealing.a2a.server;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.keiss.selfhealing.a2a.dto.*;
import de.keiss.selfhealing.a2a.protocol.*;
import de.keiss.selfhealing.core.agent.ChatClientTriageAgent;
import de.keiss.selfhealing.core.healing.*;
import de.keiss.selfhealing.core.model.FailureContext;
import de.keiss.selfhealing.core.model.HealingResult;
import de.keiss.selfhealing.core.model.TriageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import jakarta.annotation.Nullable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A2A server exposing all four self-healing skills on a single JSON-RPC endpoint. Skill is selected via
 * {@code message.metadata["skill"]}:
 * <ul>
 * <li>{@code heal-locator} — ChatClientLocatorHealer</li>
 * <li>{@code triage-failure} — ChatClientTriageAgent</li>
 * <li>{@code heal-step} — ChatClientStepHealer</li>
 * <li>{@code enrich-context} — ChatClientMcpContextEnricher (optional, may be absent)</li>
 * </ul>
 */
@Slf4j
@RestController
public class SelfHealingA2AController {

    private static final String METHOD_MESSAGE_SEND = "message/send";
    private static final String SKILL_HEAL_LOCATOR = "heal-locator";
    private static final String SKILL_TRIAGE = "triage-failure";
    private static final String SKILL_HEAL_STEP = "heal-step";
    private static final String SKILL_ENRICH = "enrich-context";
    private static final String TAG_APPIUM = "appium";

    private final ChatClientLocatorHealer locatorHealer;
    private final ChatClientTriageAgent triageAgent;
    private final ChatClientStepHealer stepHealer;
    @Nullable
    private final ChatClientMcpContextEnricher mcpEnricher;
    private final ObjectMapper objectMapper;

    @Value("${self-healing.a2a.server.base-path:/a2a}")
    private String basePath;
    @Value("${server.port:8080}")
    private int serverPort;

    public SelfHealingA2AController(ChatClientLocatorHealer locatorHealer, ChatClientTriageAgent triageAgent,
            ChatClientStepHealer stepHealer, @Nullable ChatClientMcpContextEnricher mcpEnricher,
            ObjectMapper objectMapper) {
        this.locatorHealer = locatorHealer;
        this.triageAgent = triageAgent;
        this.stepHealer = stepHealer;
        this.mcpEnricher = mcpEnricher;
        this.objectMapper = objectMapper;
    }

    @GetMapping(path = "/.well-known/agent.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentCard agentCard() {
        String url = "http://localhost:" + serverPort + basePath;
        return new AgentCard("appium-self-healing",
                "Self-healing pipeline for Appium tests. Exposes four skills: locator healing, "
                        + "triage, step healing, and MCP-based context enrichment. "
                        + "Select the skill via message.metadata[\"skill\"].",
                url, "0.2.0", new AgentCard.Capabilities(false, false, false),
                List.of(MediaType.APPLICATION_JSON_VALUE), List.of(MediaType.APPLICATION_JSON_VALUE),
                List.of(new AgentCard.Skill(SKILL_HEAL_LOCATOR, "Heal locator",
                        "Given a FailureContext, return a HealingResult with a proposed replacement locator",
                        List.of(TAG_APPIUM, "locator", "self-healing"), List.of(), null, null),
                        new AgentCard.Skill(SKILL_TRIAGE, "Triage failure",
                                "Classify the root cause of a test failure into LOCATOR_CHANGED, TEST_LOGIC_ERROR, ENVIRONMENT_ISSUE, or APP_BUG",
                                List.of(TAG_APPIUM, "triage"), List.of(), null, null),
                        new AgentCard.Skill(SKILL_HEAL_STEP, "Heal step",
                                "Suggest code-level fixes for a step definition or page object when test logic needs adjustment",
                                List.of(TAG_APPIUM, "step", "self-healing"), List.of(), null, null),
                        new AgentCard.Skill(SKILL_ENRICH, "Enrich context",
                                "Use Appium MCP tools to gather a fresh screenshot, page source, and element hints before healing",
                                List.of(TAG_APPIUM, "mcp", "enrichment"), List.of(), null, null)));
    }

    @PostMapping(path = "${self-healing.a2a.server.base-path:/a2a}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public JsonRpc.Response jsonRpc(@RequestBody JsonRpc.Request request) {
        String id = request.id() != null ? request.id() : UUID.randomUUID().toString();
        try {
            if (!METHOD_MESSAGE_SEND.equals(request.method())) {
                return JsonRpc.Response.error(id, -32601, "Method not supported: " + request.method());
            }
            A2AMessage message = extractMessage(request.params());
            String skill = extractSkill(message);
            log.info("A2A server: skill='{}' contextId={}", skill, message.contextId());

            return switch (skill) {
                case SKILL_HEAL_LOCATOR -> handleHealLocator(id, message);
                case SKILL_TRIAGE -> handleTriage(id, message);
                case SKILL_HEAL_STEP -> handleHealStep(id, message);
                case SKILL_ENRICH -> handleEnrichContext(id, message);
                default -> JsonRpc.Response.error(id, -32602, "Unknown skill: " + skill);
            };
        } catch (IllegalArgumentException e) {
            log.warn("A2A server: invalid params: {}", e.getMessage());
            return JsonRpc.Response.error(id, -32602, "Invalid params: " + e.getMessage());
        } catch (Exception e) {
            log.error("A2A server: internal error", e);
            return JsonRpc.Response.error(id, -32603, "Internal error: " + e.getMessage());
        }
    }

    // ── Skill handlers ──────────────────────────────────────────────────────

    private JsonRpc.Response handleHealLocator(String id, A2AMessage message) {
        FailureContextDto dto = extractDto(message, FailureContextDto.class);
        FailureContext context = DtoMapper.fromDto(dto);
        HealingResult result = locatorHealer.heal(context);
        HealingResultDto resultDto = DtoMapper.toDto(result);
        return JsonRpc.Response.success(id, objectMapper.valueToTree(buildTask(message, "heal-result", resultDto)));
    }

    private JsonRpc.Response handleTriage(String id, A2AMessage message) {
        FailureContextDto dto = extractDto(message, FailureContextDto.class);
        FailureContext context = DtoMapper.fromDto(dto);
        TriageResult result = triageAgent.analyze(context);
        TriageResultDto resultDto = DtoMapper.toDto(result);
        return JsonRpc.Response.success(id, objectMapper.valueToTree(buildTask(message, "triage-result", resultDto)));
    }

    private JsonRpc.Response handleHealStep(String id, A2AMessage message) {
        FailureContextDto dto = extractDto(message, FailureContextDto.class);
        FailureContext context = DtoMapper.fromDto(dto);
        StepHealingResult result = stepHealer.heal(context);
        StepHealingResultDto resultDto = DtoMapper.toDto(result);
        return JsonRpc.Response.success(id, objectMapper.valueToTree(buildTask(message, "step-result", resultDto)));
    }

    private JsonRpc.Response handleEnrichContext(String id, A2AMessage message) {
        if (mcpEnricher == null) {
            return JsonRpc.Response.error(id, -32603,
                    "enrich-context skill not available: MCP is not enabled on this server");
        }
        FailureContextDto dto = extractDto(message, FailureContextDto.class);
        FailureContext context = DtoMapper.fromDto(dto);
        FailureContext enriched = mcpEnricher.enrich(context);
        FailureContextDto enrichedDto = DtoMapper.toDto(enriched);
        return JsonRpc.Response.success(id,
                objectMapper.valueToTree(buildTask(message, "enriched-context", enrichedDto)));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String extractSkill(A2AMessage message) {
        if (message.metadata() != null && message.metadata().containsKey("skill")) {
            return String.valueOf(message.metadata().get("skill"));
        }
        // Backward compatibility: no skill metadata → assume heal-locator
        return SKILL_HEAL_LOCATOR;
    }

    private A2AMessage extractMessage(JsonNode params) {
        if (params == null || !params.has("message")) {
            throw new IllegalArgumentException("Missing 'message' in params");
        }
        try {
            return objectMapper.treeToValue(params.get("message"), A2AMessage.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed message: " + e.getMessage(), e);
        }
    }

    private <T> T extractDto(A2AMessage message, Class<T> type) {
        if (message.parts() == null) {
            throw new IllegalArgumentException("Message has no parts");
        }
        for (A2APart part : message.parts()) {
            if (part instanceof A2APart.DataPart(var data)) {
                return objectMapper.convertValue(data, type);
            }
        }
        throw new IllegalArgumentException("Message has no data part carrying " + type.getSimpleName());
    }

    private A2ATask buildTask(A2AMessage message, String artifactName, Object resultDto) {
        String taskId = message.taskId() != null ? message.taskId() : UUID.randomUUID().toString();
        String contextId = message.contextId() != null ? message.contextId() : UUID.randomUUID().toString();
        Map<String, Object> asMap = objectMapper.convertValue(resultDto, new tools.jackson.core.type.TypeReference<>() {
        });
        A2ATask.Artifact artifact = new A2ATask.Artifact(artifactName, resultDto.getClass().getSimpleName(),
                List.of(new A2APart.DataPart(asMap)));
        return A2ATask.completed(taskId, contextId, artifact);
    }
}
