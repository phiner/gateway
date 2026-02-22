package phiner.de5.net.gateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.config.ForexProperties;
import phiner.de5.net.gateway.service.RedisService;

/**
 * Initializes Redis with configuration from the environment on startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisInitializer implements ApplicationRunner {

    private final RedisService redisService;
    private final ForexProperties forexProperties;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Initializing Redis configuration...");
        
        if (forexProperties.getInstruments() != null) {
            redisService.saveConfigInstruments(forexProperties.getInstruments());
        } else {
            log.warn("FOREX_INSTRUMENTS not found in configuration.");
        }

        if (forexProperties.getPeriods() != null) {
            redisService.saveConfigPeriods(forexProperties.getPeriods());
        } else {
            log.warn("FOREX_PERIODS not found in configuration.");
        }
        
        log.info("Redis configuration initialization complete.");
    }
}
