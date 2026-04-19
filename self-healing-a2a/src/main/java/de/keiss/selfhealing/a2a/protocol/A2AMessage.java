package de.keiss.selfhealing.a2a.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * A2A message — what a client sends in {@code message/send.params.message} and what a server may return as part of a
 * Task. The spec has a longer list of optional fields; we keep only the ones we actually use.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record A2AMessage(String role, List<A2APart> parts, String messageId, String taskId, String contextId,
        Map<String, Object> metadata) {

    public A2AMessage(String role, List<A2APart> parts, String messageId) {
        this(role, parts, messageId, null, null, null);
    }
}
