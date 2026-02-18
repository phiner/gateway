package phiner.de5.net.gateway.manager;

import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import phiner.de5.net.gateway.dto.TickDTO;

/**
 * Manages the WebSocket connections and broadcasts data to clients.
 * Implemented as a thread-safe singleton.
 */
public final class WebSocketManager {

    // The single instance of the WebSocketManager.
    private static final WebSocketManager INSTANCE = new WebSocketManager();

    // A thread-safe set to hold all active WebSocket channels.
    private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    // Private constructor to prevent instantiation.
    private WebSocketManager() {}

    /**
     * Returns the singleton instance of the WebSocketManager.
     */
    public static WebSocketManager getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the channel group.
     * @return the channel group
     */
    public ChannelGroup getChannels() {
        return channels;
    }

    /**
     * Broadcasts a TickDTO to all connected WebSocket clients.
     * This method is thread-safe as it delegates the write operation to the ChannelGroup.
     *
     * @param tick The TickDTO to be broadcast.
     */
    public void broadcast(TickDTO tick) {
        if (tick != null && !channels.isEmpty()) {
            // The writeAndFlush operation on ChannelGroup is thread-safe.
            // It will write the message to all channels in the group.
            channels.writeAndFlush(tick);
        }
    }
}
