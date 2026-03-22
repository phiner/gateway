package phiner.de5.net.gateway.listener;

import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.request.OpenMarketOrderRequest;
import phiner.de5.net.gateway.service.RedisService;
import phiner.de5.net.gateway.strategy.TradingStrategy;

@Component
public class OrderOpenListener extends AbstractRequestListener<OpenMarketOrderRequest> {

    public OrderOpenListener(TradingStrategy tradingStrategy, RedisService redisService) {
        super(tradingStrategy, redisService, OpenMarketOrderRequest.class, "market order open");
    }

    @Override
    protected void executeRequest(OpenMarketOrderRequest request) {
        tradingStrategy.executeMarketOrder(request);
    }
}
