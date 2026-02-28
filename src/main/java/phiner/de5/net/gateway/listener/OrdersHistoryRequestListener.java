package phiner.de5.net.gateway.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.MsgpackDecoder;
import phiner.de5.net.gateway.dto.OrdersHistoryRequest;
import phiner.de5.net.gateway.strategy.TradingStrategy;

import java.util.Optional;

@Slf4j
@Component
public class OrdersHistoryRequestListener implements MessageListener {

    private final TradingStrategy tradingStrategy;

    public OrdersHistoryRequestListener(TradingStrategy tradingStrategy) {
        this.tradingStrategy = tradingStrategy;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        log.info("Received orders history request on channel: {}", new String(message.getChannel()));
        Optional.ofNullable(MsgpackDecoder.decode(message.getBody(), OrdersHistoryRequest.class))
                .ifPresentOrElse(
                        tradingStrategy::handleOrdersHistoryRequest,
                        () -> log.error("Failed to decode OrdersHistoryRequest from Redis message")
                );
    }
}
