package de.keiss.selfhealing.a2a.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

/**
 * Content fragment inside an A2A message or artifact. The A2A spec defines {@code text}, {@code data}, and {@code file}
 * variants. We only need text and data here.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({@JsonSubTypes.Type(value = A2APart.TextPart.class, name = "text"),
        @JsonSubTypes.Type(value = A2APart.DataPart.class, name = "data")})
public sealed interface A2APart {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TextPart(String text) implements A2APart {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DataPart(Map<String, Object> data) implements A2APart {
    }
}
