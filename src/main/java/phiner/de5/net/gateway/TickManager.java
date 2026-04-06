package phiner.de5.net.gateway;

import com.dukascopy.api.ITick;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.dto.TickDTO;
import phiner.de5.net.gateway.service.ForexTickProducer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class TickManager {
    private final Map<String, TickDTO> lastTicks = Collections.synchronizedMap(new HashMap<>());
    private final ForexTickProducer forexTickProducer;

    public TickManager(ForexTickProducer forexTickProducer) {
        this.forexTickProducer = forexTickProducer;
    }

    public void onTick(@NonNull String instrument, @NonNull ITick tick) {
        // 更新内存中的最新 Tick (用于内部状态查询)
        TickDTO tickDTO = new TickDTO(
                instrument,
                tick.getTime(),
                tick.getAsk(),
                tick.getBid()
        );
        lastTicks.put(instrument, tickDTO);

        // 核心变更：使用高性能 Redis Stream 写入，替换原有的 Pub/Sub 模式
        forexTickProducer.sendTickAsync(
                instrument,
                tick.getTime(),
                tick.getBid(),
                tick.getAsk(),
                tick.getBidVolume(),
                tick.getAskVolume()
        );
    }

    public TickDTO getLastTick(String instrument) {
        return lastTicks.get(instrument);
    }
}
