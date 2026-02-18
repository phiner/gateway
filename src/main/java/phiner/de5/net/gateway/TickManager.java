package phiner.de5.net.gateway;

import com.dukascopy.api.ITick;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.dto.TickDTO;
import phiner.de5.net.gateway.service.RedisService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class TickManager {
    private final Map<String, TickDTO> lastTicks = Collections.synchronizedMap(new HashMap<>());
    private final RedisService redisService;

    public TickManager(RedisService redisService) {
        this.redisService = redisService;
    }

    public void onTick(@NonNull String instrument, @NonNull ITick tick) {
        TickDTO tickDTO = new TickDTO(
                instrument,
                tick.getTime(),
                tick.getAsk(),
                tick.getBid()
        );
        synchronized (lastTicks) {
            lastTicks.put(instrument, tickDTO);
            redisService.publishTick(tickDTO);
        }
    }

    public TickDTO getLastTick(String instrument) {
        return lastTicks.get(instrument);
    }
}
