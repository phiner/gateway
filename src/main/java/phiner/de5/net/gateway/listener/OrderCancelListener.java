package phiner.de5.net.gateway.listener;

import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.request.CancelOrderRequest;
import phiner.de5.net.gateway.service.RedisService;
import phiner.de5.net.gateway.strategy.TradingStrategy;

@Component
public class OrderCancelListener extends AbstractRequestListener<CancelOrderRequest> {

    public OrderCancelListener(TradingStrategy tradingStrategy, RedisService redisService) {
        super(tradingStrategy, redisService, CancelOrderRequest.class, "order cancel");
    }

    @Override
    protected void executeRequest(CancelOrderRequest request) {
        tradingStrategy.cancelOrder(request);
    }
}
