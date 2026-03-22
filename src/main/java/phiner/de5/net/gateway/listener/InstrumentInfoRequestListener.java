package phiner.de5.net.gateway.listener;

import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.request.InstrumentInfoRequest;
import phiner.de5.net.gateway.service.RedisService;
import phiner.de5.net.gateway.strategy.TradingStrategy;

@Component
public class InstrumentInfoRequestListener extends AbstractRequestListener<InstrumentInfoRequest> {

    public InstrumentInfoRequestListener(TradingStrategy tradingStrategy, RedisService redisService) {
        super(tradingStrategy, redisService, InstrumentInfoRequest.class, "instrument info request");
    }

    @Override
    protected void executeRequest(InstrumentInfoRequest request) {
        tradingStrategy.handleInstrumentInfoRequest(request);
    }
}
