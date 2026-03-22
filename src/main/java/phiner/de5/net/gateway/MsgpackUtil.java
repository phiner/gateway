package phiner.de5.net.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MsgpackUtil {

    private static final Logger log = LoggerFactory.getLogger(MsgpackUtil.class);
    private static final ObjectMapper MAPPER = new ObjectMapper(new MessagePackFactory());

    private MsgpackUtil() {}

    public static byte[] encode(Object obj) {
        try {
            return MAPPER.writeValueAsBytes(obj);
        } catch (Exception e) {
            log.error("Failed to encode object to MessagePack", e);
            return null;
        }
    }

    public static <T> T decode(byte[] data, Class<T> clazz) {
        if (data == null) {
            return null;
        }
        try {
            return MAPPER.readValue(data, clazz);
        } catch (Exception e) {
            log.error("Failed to decode MessagePack data", e);
            return null;
        }
    }
}
