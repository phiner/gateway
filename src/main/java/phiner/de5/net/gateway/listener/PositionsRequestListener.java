package phiner.de5.net.gateway.listener;

import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.request.PositionsRequest;
import phiner.de5.net.gateway.service.RedisService;
import phiner.de5.net.gateway.strategy.TradingStrategy;

@Component
public class PositionsRequestListener extends AbstractRequestListener<PositionsRequest> {

    public PositionsRequestListener(TradingStrategy tradingStrategy, RedisService redisService) {
        super(tradingStrategy, redisService, PositionsRequest.class, "positions request");
    }

    @Override
    protected void executeRequest(PositionsRequest request) {
        tradingStrategy.handlePositionsRequest(request);
    }
}
