package de.keiss.selfhealing.a2a.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Agent Card served at {@code /.well-known/agent.json}. Mirrors the subset of the A2A Agent Card schema that this spike
 * actually uses — name, URL, capabilities, input/output modes, skills. Fields we do not populate (provider,
 * documentationUrl, security) are simply omitted via {@link JsonInclude.Include#NON_NULL}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentCard(String name, String description, String url, String version, Capabilities capabilities,
        List<String> defaultInputModes, List<String> defaultOutputModes, List<Skill> skills) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Capabilities(boolean streaming, boolean pushNotifications, boolean stateTransitionHistory) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Skill(String id, String name, String description, List<String> tags, List<String> examples,
            List<String> inputModes, List<String> outputModes) {
    }
}
