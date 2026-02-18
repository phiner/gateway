package phiner.de5.net.gateway.listener;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.MsgpackDecoder;
import phiner.de5.net.gateway.request.ModifyOrderRequest;
import phiner.de5.net.gateway.service.RedisService;
import phiner.de5.net.gateway.strategy.TradingStrategy;

@Component
public class OrderModifyListener implements MessageListener {

    private final TradingStrategy tradingStrategy;
    private final RedisService redisService;

    public OrderModifyListener(TradingStrategy tradingStrategy, RedisService redisService) {
        this.tradingStrategy = tradingStrategy;
        this.redisService = redisService;
    }

    @Override
    public void onMessage(@NonNull Message message, @Nullable byte[] pattern) {
        try {
            ModifyOrderRequest request = MsgpackDecoder.decode(message.getBody(), ModifyOrderRequest.class);
            if (request != null) {
                tradingStrategy.modifyOrder(request);
            }
        } catch (Exception e) {
            redisService.publishError("Failed to modify order: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
