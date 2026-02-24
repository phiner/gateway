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
        } catch (Exception e) {
            redisService.publishError("无法处理开仓市场订单请求: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
