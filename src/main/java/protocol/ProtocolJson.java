package protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import util.ValidationException;

public final class ProtocolJson {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private ProtocolJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String write(ProtocolMessage message) {
        try {
            return MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Nie można zapisać wiadomości JSON.");
        }
    }

    public static ProtocolMessage read(String line) {
        try {
            ProtocolMessage message = MAPPER.readValue(line, ProtocolMessage.class);
            if (message.getType() == null) {
                throw new ValidationException("Wiadomość JSON nie ma typu.");
            }
            return message;
        } catch (JsonProcessingException e) {
            throw new ValidationException("Niepoprawna wiadomość JSON.");
        }
    }

    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    public static JsonNode toJson(Object value) {
        return MAPPER.valueToTree(value);
    }

    public static <T> T fromJson(JsonNode node, Class<T> type) {
        try {
            return MAPPER.treeToValue(node, type);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Niepoprawne dane w wiadomości JSON.");
        }
    }

    public static ProtocolMessage ok(MessageType type, Object payload) {
        return ProtocolMessage.of(type, toJson(payload));
    }

    public static ProtocolMessage error(String message) {
        ObjectNode payload = object();
        payload.put("success", false);
        payload.put("message", message);
        return ProtocolMessage.of(MessageType.ERROR_RESPONSE, payload);
    }
}
