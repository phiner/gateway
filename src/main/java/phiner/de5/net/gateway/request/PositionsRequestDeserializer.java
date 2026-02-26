package phiner.de5.net.gateway.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public class PositionsRequestDeserializer extends JsonDeserializer<PositionsRequest> {

    @Override
    public PositionsRequest deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();

        if (token == JsonToken.START_ARRAY) {
            // Rust serialized as a Tuple String/Array: ["requestId"]
            // Advance to the first element
            p.nextToken();
            String requestId = p.getValueAsString();
            
            // Advance to END_ARRAY
            while (p.nextToken() != JsonToken.END_ARRAY && p.currentToken() != null) {
                // skip over remaining array elements if any
            }
            
            return new PositionsRequest(requestId);
        } else if (token == JsonToken.START_OBJECT) {
            // normal mapping {"requestId": "..."}
            JsonNode node = p.getCodec().readTree(p);
            String requestId = node.has("requestId") ? node.get("requestId").asText() : null;
            return new PositionsRequest(requestId);
        }

        throw ctxt.instantiationException(PositionsRequest.class, "Expected START_ARRAY or START_OBJECT, got: " + token);
    }
}
