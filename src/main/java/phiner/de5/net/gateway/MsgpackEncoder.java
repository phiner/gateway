package phiner.de5.net.gateway;

@Deprecated
public class MsgpackEncoder {

    public static byte[] encode(Object obj) {
        return MsgpackUtil.encode(obj);
    }
}
