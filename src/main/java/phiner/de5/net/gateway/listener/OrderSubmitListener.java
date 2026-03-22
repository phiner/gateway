package phiner.de5.net.gateway.listener;

import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.request.SubmitOrderRequest;
import phiner.de5.net.gateway.service.RedisService;
import phiner.de5.net.gateway.strategy.TradingStrategy;

@Component
public class OrderSubmitListener extends AbstractRequestListener<SubmitOrderRequest> {

    public OrderSubmitListener(TradingStrategy tradingStrategy, RedisService redisService) {
        super(tradingStrategy, redisService, SubmitOrderRequest.class, "order submit");
    }

    @Override
    protected void executeRequest(SubmitOrderRequest request) {
        tradingStrategy.submitOrder(request);
    }
}
