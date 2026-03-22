package phiner.de5.net.gateway;

@Deprecated
public class MsgpackDecoder {

    public static <T> T decode(byte[] data, Class<T> clazz) {
        return MsgpackUtil.decode(data, clazz);
    }
}
