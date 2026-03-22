package phiner.de5.net.gateway.listener;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.MsgpackUtil;
import phiner.de5.net.gateway.dto.OrdersHistoryRequest;
import phiner.de5.net.gateway.strategy.TradingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Component
public class OrdersHistoryRequestListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(OrdersHistoryRequestListener.class);
    private final TradingStrategy tradingStrategy;

    public OrdersHistoryRequestListener(TradingStrategy tradingStrategy) {
        this.tradingStrategy = tradingStrategy;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        log.info("Received orders history request on channel: {}", new String(message.getChannel()));
        Optional.ofNullable(MsgpackUtil.decode(message.getBody(), OrdersHistoryRequest.class))
                .ifPresentOrElse(
                        tradingStrategy::handleOrdersHistoryRequest,
                        () -> log.error("Failed to decode OrdersHistoryRequest from Redis message")
                );
    }
}
