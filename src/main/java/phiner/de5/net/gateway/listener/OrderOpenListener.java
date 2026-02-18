package phiner.de5.net.gateway.listener;

import com.dukascopy.api.JFException;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.MsgpackDecoder;
import phiner.de5.net.gateway.request.OpenMarketOrderRequest;
import phiner.de5.net.gateway.service.RedisService;
import phiner.de5.net.gateway.strategy.TradingStrategy;

@Component
public class OrderOpenListener implements MessageListener {

    private final TradingStrategy tradingStrategy;
    private final RedisService redisService;

    public OrderOpenListener(TradingStrategy tradingStrategy, RedisService redisService) {
        this.tradingStrategy = tradingStrategy;
        this.redisService = redisService;
    }

    @Override
    public void onMessage(@NonNull Message message, @Nullable byte[] pattern) {
        try {
            OpenMarketOrderRequest request = MsgpackDecoder.decode(message.getBody(), OpenMarketOrderRequest.class);
            if (request != null) {
                tradingStrategy.executeMarketOrder(request);
            }
        } catch (JFException e) {
            redisService.publishError("Failed to execute open market order: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            redisService.publishError("Failed to process open market order request: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
