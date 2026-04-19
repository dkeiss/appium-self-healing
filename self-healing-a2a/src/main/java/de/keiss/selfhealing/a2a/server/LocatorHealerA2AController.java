package de.keiss.selfhealing.a2a.server;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.keiss.selfhealing.a2a.dto.DtoMapper;
import de.keiss.selfhealing.a2a.dto.FailureContextDto;
import de.keiss.selfhealing.a2a.dto.HealingResultDto;
import de.keiss.selfhealing.a2a.protocol.A2AMessage;
import de.keiss.selfhealing.a2a.protocol.A2APart;
import de.keiss.selfhealing.a2a.protocol.A2ATask;
import de.keiss.selfhealing.a2a.protocol.AgentCard;
import de.keiss.selfhealing.a2a.protocol.JsonRpc;
import de.keiss.selfhealing.core.healing.ChatClientLocatorHealer;
import de.keiss.selfhealing.core.model.FailureContext;
import de.keiss.selfhealing.core.model.HealingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A2A server for the {@code locator-healer} skill. Exposes two endpoints:
 *
 * <ul>
 * <li>{@code GET /.well-known/agent.json} — Agent Card describing the skill.</li>
 * <li>{@code POST <basePath>} — JSON-RPC 2.0 entrypoint. Only {@code message/send} is handled; the message must carry a
 * {@code data} part whose payload is a {@link FailureContextDto}. The response is an A2A {@link A2ATask} with one
 * artifact containing a {@link HealingResultDto}.</li>
 * </ul>
 *
 * This controller is deliberately kept minimal: no streaming, no task persistence, no push notifications. It suffices
 * for the spike and documents the wire format the client adapter must produce.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class LocatorHealerA2AController {

    private static final String SKILL_ID = "heal-locator";
    private static final String METHOD_MESSAGE_SEND = "message/send";

    private final ChatClientLocatorHealer healer;
    private final ObjectMapper objectMapper;
    @Value("${self-healing.a2a.server.base-path:/a2a}")
    private String basePath;
    @Value("${server.port:8080}")
    private int serverPort;

    @GetMapping(path = "/.well-known/agent.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentCard agentCard() {
        String url = "http://localhost:" + serverPort + basePath;
        return new AgentCard("appium-self-healing-locator",
                "Heals broken Appium locators using LLM analysis of the page source, failure context, "
                        + "and (optionally) a screenshot. Invoke with skill id '" + SKILL_ID
                        + "' and a data part carrying a FailureContextDto.",
                url, "0.1.0", new AgentCard.Capabilities(false, false, false),
                List.of(MediaType.APPLICATION_JSON_VALUE), List.of(MediaType.APPLICATION_JSON_VALUE),
                List.of(new AgentCard.Skill(SKILL_ID, "Heal locator",
                        "Given a FailureContext, return a HealingResult with a proposed replacement locator",
                        List.of("appium", "self-healing", "locator"), List.of(), null, null)));
    }

    @PostMapping(path = "${self-healing.a2a.server.base-path:/a2a}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public JsonRpc.Response jsonRpc(@RequestBody JsonRpc.Request request) {
        String id = request.id() != null ? request.id() : UUID.randomUUID().toString();
        try {
            if (!METHOD_MESSAGE_SEND.equals(request.method())) {
                return JsonRpc.Response.error(id, -32601, "Method not supported: " + request.method());
            }
            A2AMessage message = extractMessage(request.params());
            FailureContextDto dto = extractFailureContextDto(message);

            log.info("A2A server: message/send received for stepName='{}', contextId={}", dto.stepName(),
                    message.contextId());

            FailureContext context = DtoMapper.fromDto(dto);
            HealingResult result = healer.heal(context);
            HealingResultDto resultDto = DtoMapper.toDto(result);

            A2ATask task = buildTask(message, resultDto);
            return JsonRpc.Response.success(id, objectMapper.valueToTree(task));
        } catch (IllegalArgumentException e) {
            log.warn("A2A server: invalid params: {}", e.getMessage());
            return JsonRpc.Response.error(id, -32602, "Invalid params: " + e.getMessage());
        } catch (Exception e) {
            log.error("A2A server: internal error", e);
            return JsonRpc.Response.error(id, -32603, "Internal error: " + e.getMessage());
        }
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

    private FailureContextDto extractFailureContextDto(A2AMessage message) {
        if (message.parts() == null) {
            throw new IllegalArgumentException("Message has no parts");
        }
        for (A2APart part : message.parts()) {
            if (part instanceof A2APart.DataPart dp) {
                return objectMapper.convertValue(dp.data(), FailureContextDto.class);
            }
        }
        throw new IllegalArgumentException("Message has no data part carrying FailureContextDto");
    }

    private A2ATask buildTask(A2AMessage message, HealingResultDto resultDto) {
        String taskId = message.taskId() != null ? message.taskId() : UUID.randomUUID().toString();
        String contextId = message.contextId() != null ? message.contextId() : UUID.randomUUID().toString();
        Map<String, Object> asMap = objectMapper.convertValue(resultDto, new tools.jackson.core.type.TypeReference<>() {
        });
        A2ATask.Artifact artifact = new A2ATask.Artifact("heal-result", "HealingResultDto",
                List.of(new A2APart.DataPart(asMap)));
        return A2ATask.completed(taskId, contextId, artifact);
    }
}
