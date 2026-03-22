package phiner.de5.net.gateway.listener;

import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.request.ModifyOrderRequest;
import phiner.de5.net.gateway.service.RedisService;
import phiner.de5.net.gateway.strategy.TradingStrategy;

@Component
public class OrderModifyListener extends AbstractRequestListener<ModifyOrderRequest> {

    public OrderModifyListener(TradingStrategy tradingStrategy, RedisService redisService) {
        super(tradingStrategy, redisService, ModifyOrderRequest.class, "order modify");
    }

    @Override
    protected void executeRequest(ModifyOrderRequest request) {
        tradingStrategy.modifyOrder(request);
    }
}
