package de.keiss.selfhealing.a2a.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Task returned from {@code message/send}. We only use the synchronous, single-shot form — status goes straight to
 * {@code completed} or {@code failed}, and the payload is carried in exactly one artifact.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record A2ATask(String id, String contextId, Status status, List<Artifact> artifacts, String kind) {

    public static A2ATask completed(String id, String contextId, Artifact artifact) {
        return new A2ATask(id, contextId, new Status("completed"), List.of(artifact), "task");
    }

    public static A2ATask failed(String id, String contextId, String message) {
        return new A2ATask(id, contextId, new Status("failed", message), List.of(), "task");
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Status(String state, String message) {

        public Status(String state) {
            this(state, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Artifact(String artifactId, String name, List<A2APart> parts) {
    }
}
