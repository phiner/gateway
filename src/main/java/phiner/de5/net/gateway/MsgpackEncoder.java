package phiner.de5.net.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class MsgpackEncoder {

    private static final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());

    public static byte[] encode(Object obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to encode object to MessagePack", e);
        }
    }
}
