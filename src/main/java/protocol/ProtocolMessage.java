package protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public class ProtocolMessage {
    private MessageType type;
    private JsonNode payload;

    public ProtocolMessage() {
    }

    public ProtocolMessage(MessageType type, JsonNode payload) {
        this.type = type;
        this.payload = payload;
    }

    public static ProtocolMessage of(MessageType type, JsonNode payload) {
        return new ProtocolMessage(type, payload == null ? JsonNodeFactory.instance.objectNode() : payload);
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }
}
