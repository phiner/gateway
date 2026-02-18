package phiner.de5.net.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.IOException;

public class MsgpackDecoder {

    private static final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());

    public static <T> T decode(byte[] data, Class<T> clazz) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.readValue(data, clazz);
        } catch (IOException e) {
            System.err.println("Failed to decode MessagePack data: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
