package phiner.de5.net.gateway;

import com.dukascopy.api.ITick;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.dto.TickDTO;
import phiner.de5.net.gateway.service.ForexTickProducer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class TickManager {
    private static final Logger log = LoggerFactory.getLogger(TickManager.class);
    private final Map<String, TickDTO> lastTicks = Collections.synchronizedMap(new HashMap<>());
    private final ForexTickProducer forexTickProducer;
    private volatile boolean enabled = false;

    public TickManager(ForexTickProducer forexTickProducer) {
        this.forexTickProducer = forexTickProducer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        this.enabled = true;
        log.info("TickManager 已激活：应用已完全启动并连接，行情行情写入管道已开启。");
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

        // 仅在系统就绪后向 Redis Stream 写入
        if (enabled) {
            forexTickProducer.sendTickAsync(
                    instrument,
                    tick.getTime(),
                    tick.getBid(),
                    tick.getAsk(),
                    tick.getBidVolume(),
                    tick.getAskVolume()
            );
        }
    }

    public TickDTO getLastTick(String instrument) {
        return lastTicks.get(instrument);
    }
}
