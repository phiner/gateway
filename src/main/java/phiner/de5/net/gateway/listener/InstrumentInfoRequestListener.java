package phiner.de5.net.gateway.listener;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.MsgpackDecoder;
import phiner.de5.net.gateway.request.InstrumentInfoRequest;
import phiner.de5.net.gateway.strategy.TradingStrategy;

@Component
public class InstrumentInfoRequestListener implements MessageListener {

    private final TradingStrategy tradingStrategy;

    public InstrumentInfoRequestListener(TradingStrategy tradingStrategy) {
        this.tradingStrategy = tradingStrategy;
    }

    @Override
    public void onMessage(@NonNull Message message, @Nullable byte[] pattern) {
        try {
            InstrumentInfoRequest request = MsgpackDecoder.decode(message.getBody(), InstrumentInfoRequest.class);
            if (request != null) {
                tradingStrategy.handleInstrumentInfoRequest(request);
            }
        } catch (Exception e) {
            System.err.println("Failed to process instrument info request: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
