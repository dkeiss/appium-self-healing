package de.keiss.selfhealing.a2a.client;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.keiss.selfhealing.a2a.dto.FailureContextDto;
import de.keiss.selfhealing.a2a.dto.HealingResultDto;
import de.keiss.selfhealing.a2a.protocol.A2AMessage;
import de.keiss.selfhealing.a2a.protocol.A2APart;
import de.keiss.selfhealing.a2a.protocol.A2ATask;
import de.keiss.selfhealing.a2a.protocol.JsonRpc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal A2A client for the {@code heal-locator} skill. Sends a JSON-RPC {@code message/send} request with a single
 * data part carrying the {@link FailureContextDto} and extracts the {@link HealingResultDto} from the resulting task's
 * first artifact.
 */
@Slf4j
public class A2AClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;

    public A2AClient(String baseUrl, ObjectMapper objectMapper, Duration requestTimeout) {
        this.objectMapper = objectMapper;
        this.requestTimeout = requestTimeout;
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Math.min(requestTimeout.toMillis(), Integer.MAX_VALUE));
        requestFactory.setReadTimeout((int) Math.min(requestTimeout.toMillis(), Integer.MAX_VALUE));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public HealingResultDto healLocator(FailureContextDto input) {
        String rpcId = UUID.randomUUID().toString();
        Map<String, Object> dataMap = objectMapper.convertValue(input, new TypeReference<>() {
        });
        A2AMessage message = new A2AMessage("user", List.of(new A2APart.DataPart(dataMap)),
                UUID.randomUUID().toString());
        JsonNode paramsNode = objectMapper.valueToTree(Map.of("message", message));
        JsonRpc.Request request = new JsonRpc.Request(rpcId, "message/send", paramsNode);

        log.info("A2A client: sending message/send (rpc id={}, stepName={}) — timeout {}ms", rpcId, input.stepName(),
                requestTimeout.toMillis());

        JsonRpc.Response response = restClient.post().contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON).body(request).retrieve().body(JsonRpc.Response.class);

        if (response == null) {
            throw new IllegalStateException("A2A server returned empty response body");
        }
        if (response.error() != null) {
            throw new IllegalStateException("A2A server returned JSON-RPC error " + response.error().code() + ": "
                    + response.error().message());
        }
        A2ATask task;
        try {
            task = objectMapper.treeToValue(response.result(), A2ATask.class);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse A2ATask from JSON-RPC result", e);
        }
        return extractHealingResult(task);
    }

    private HealingResultDto extractHealingResult(A2ATask task) {
        if (task.status() != null && "failed".equals(task.status().state())) {
            throw new IllegalStateException("A2A task failed: " + task.status().message());
        }
        if (task.artifacts() == null || task.artifacts().isEmpty()) {
            throw new IllegalStateException("A2A task has no artifacts");
        }
        A2ATask.Artifact artifact = task.artifacts().get(0);
        if (artifact.parts() == null) {
            throw new IllegalStateException("A2A artifact has no parts");
        }
        for (A2APart part : artifact.parts()) {
            if (part instanceof A2APart.DataPart dp) {
                return objectMapper.convertValue(dp.data(), HealingResultDto.class);
            }
        }
        throw new IllegalStateException("A2A artifact has no data part carrying a HealingResultDto");
    }
}
