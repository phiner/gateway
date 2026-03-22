package phiner.de5.net.gateway.listener;

import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.request.CloseMarketOrderRequest;
import phiner.de5.net.gateway.service.RedisService;
import phiner.de5.net.gateway.strategy.TradingStrategy;

@Component
public class OrderCloseListener extends AbstractRequestListener<CloseMarketOrderRequest> {

    public OrderCloseListener(TradingStrategy tradingStrategy, RedisService redisService) {
        super(tradingStrategy, redisService, CloseMarketOrderRequest.class, "market order close");
    }

    @Override
    protected void executeRequest(CloseMarketOrderRequest request) {
        tradingStrategy.closeMarketOrder(request);
    }
}
