package phiner.de5.net.gateway;

import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.dto.BarDTO;
import phiner.de5.net.gateway.service.RedisService;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KLineManager {
    private final ConcurrentHashMap<String, BarDTO> lastBars = new ConcurrentHashMap<>();
    private final RedisService redisService;

    public KLineManager(RedisService redisService) {
        this.redisService = redisService;
    }

    public Set<String> getSubscribedInstruments() {
        return lastBars.keySet();
    }

    public void onBar(String instrument, BarDTO bar) {
        if (instrument == null || bar == null) {
            return;
        }

        lastBars.put(instrument, bar);
        redisService.addBarToKLine(bar);
        redisService.publishBar(bar);
    }

    public BarDTO getLastBar(String instrument) {
        return lastBars.get(instrument);
    }
}
