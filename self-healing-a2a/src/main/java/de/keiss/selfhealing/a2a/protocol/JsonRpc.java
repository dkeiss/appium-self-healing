package de.keiss.selfhealing.a2a.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

/**
 * Minimal JSON-RPC 2.0 envelope types shared by server and client. Fields match the A2A wire format.
 */
public final class JsonRpc {

    public static final String VERSION = "2.0";

    private JsonRpc() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(String jsonrpc, String id, String method, JsonNode params) {

        public Request(String id, String method, JsonNode params) {
            this(VERSION, id, method, params);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(String jsonrpc, String id, JsonNode result, Error error) {

        public static Response success(String id, JsonNode result) {
            return new Response(VERSION, id, result, null);
        }

        public static Response error(String id, int code, String message) {
            return new Response(VERSION, id, null, new Error(code, message, null));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Error(int code, String message, JsonNode data) {
    }
}
