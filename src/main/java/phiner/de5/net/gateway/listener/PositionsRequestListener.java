package phiner.de5.net.gateway.listener;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.MsgpackDecoder;
import phiner.de5.net.gateway.request.PositionsRequest;
import phiner.de5.net.gateway.service.RedisService;
import phiner.de5.net.gateway.strategy.TradingStrategy;

@Component
public class PositionsRequestListener implements MessageListener {

    private final TradingStrategy tradingStrategy;
    private final RedisService redisService;

    public PositionsRequestListener(TradingStrategy tradingStrategy, RedisService redisService) {
        this.tradingStrategy = tradingStrategy;
        this.redisService = redisService;
    }

    @Override
    public void onMessage(@NonNull Message message, @Nullable byte[] pattern) {
        try {
            PositionsRequest request = MsgpackDecoder.decode(message.getBody(), PositionsRequest.class);
            if (request != null) {
                tradingStrategy.handlePositionsRequest(request);
            }
        } catch (Exception e) {
            redisService.publishError("无法处理持仓请求: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
