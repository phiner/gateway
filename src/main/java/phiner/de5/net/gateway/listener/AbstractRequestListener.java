package phiner.de5.net.gateway.listener;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import phiner.de5.net.gateway.MsgpackUtil;
import phiner.de5.net.gateway.service.RedisService;
import phiner.de5.net.gateway.strategy.TradingStrategy;

public abstract class AbstractRequestListener<T> implements MessageListener {

    protected final TradingStrategy tradingStrategy;
    protected final RedisService redisService;
    protected final Class<T> requestType;
    protected final String operationName;

    protected AbstractRequestListener(
            TradingStrategy tradingStrategy,
            RedisService redisService,
            Class<T> requestType,
            String operationName) {
        this.tradingStrategy = tradingStrategy;
        this.redisService = redisService;
        this.requestType = requestType;
        this.operationName = operationName;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            T request = MsgpackUtil.decode(message.getBody(), requestType);
            if (request != null) {
                executeRequest(request);
            }
        } catch (Exception e) {
            redisService.publishError("Failed to process " + operationName + " request: " + e.getMessage());
            log.error("Error processing {} request", operationName, e);
        }
    }

    protected abstract void executeRequest(T request);

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AbstractRequestListener.class);
}
