package phiner.de5.net.gateway.listener;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.MsgpackDecoder;
import phiner.de5.net.gateway.request.SubmitOrderRequest;
import phiner.de5.net.gateway.service.RedisService;
import phiner.de5.net.gateway.strategy.TradingStrategy;

@Component
public class OrderSubmitListener implements MessageListener {

    private final TradingStrategy tradingStrategy;
    private final RedisService redisService;

    public OrderSubmitListener(TradingStrategy tradingStrategy, RedisService redisService) {
        this.tradingStrategy = tradingStrategy;
        this.redisService = redisService;
    }

    @Override
    public void onMessage(@NonNull Message message, @Nullable byte[] pattern) {
        try {
            SubmitOrderRequest request = MsgpackDecoder.decode(message.getBody(), SubmitOrderRequest.class);
            if (request != null) {
                tradingStrategy.submitOrder(request);
            }
        } catch (Exception e) {
            redisService.publishError("Failed to submit order: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
