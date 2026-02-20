package phiner.de5.net.gateway.service;

import com.dukascopy.api.system.ClientFactory;
import com.dukascopy.api.system.IClient;
import com.dukascopy.api.system.ISystemListener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import phiner.de5.net.gateway.config.JForexProperties;
import phiner.de5.net.gateway.strategy.TradingStrategy;

@Slf4j
@Service
@RequiredArgsConstructor
public class JForexClientService {

    private final JForexProperties jForexProperties;
    private final TradingStrategy tradingStrategy;
    private final RedisService redisService;
    private IClient client;

    @PostConstruct
    public void init() {
        try {
            log.info("Checking Redis connection...");
            redisService.testConnection();
            log.info("Redis connection established.");

            this.client = ClientFactory.getDefaultInstance();
            setupListener();
            connect();
        } catch (Exception e) {
            log.error("PRECONDITION FAILED: Redis must be connected before starting JForex gateway. Error: {}", e.getMessage());
            System.exit(1);
        }
    }

    private void setupListener() {
        client.setSystemListener(new ISystemListener() {
            private int lightReconnects = 3;

            @Override
            public void onStart(long processId) {
                log.info("JForex Strategy started successfully with processId: {}", processId);
            }

            @Override
            public void onStop(long processId) {
                log.info("JForex Strategy stopped with processId: {}", processId);
            }

            @Override
            public void onConnect() {
                log.info("Successfully connected to JForex server");
                lightReconnects = 3;
                startStrategy();
            }

            @Override
            public void onDisconnect() {
                log.warn("Disconnected from JForex server");
                if (lightReconnects > 0) {
                    log.info("Attempting to reconnect ({} attempts remaining)...", lightReconnects);
                    try {
                        client.reconnect();
                    } catch (Exception e) {
                        log.error("Reconnection attempt failed: {}", e.getMessage());
                    }
                    lightReconnects--;
                } else {
                    log.error("Exceeded maximum reconnection attempts.");
                }
            }
        });
    }

    private void connect() {
        try {
            log.info("Connecting to JForex platform at {}...", jForexProperties.getUrl());
            client.connect(jForexProperties.getUrl(), jForexProperties.getUsername(), jForexProperties.getPassword());
            
            // Wait for connection in a separate thread to not block Spring startup if needed, 
            // but for a simple gateway, we can just wait or rely on onConnect callback.
        } catch (Exception e) {
            log.error("Failed to initiate connection to JForex: {}", e.getMessage());
        }
    }

    private void startStrategy() {
        try {
            if (client.isConnected()) {
                log.info("Starting TradingStrategy...");
                client.startStrategy(tradingStrategy);
            }
        } catch (Exception e) {
            log.error("Failed to start strategy: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (client != null) {
            log.info("Shutting down JForex client...");
            client.stopStrategy(0); // Stop all strategies
            // client.disconnect(); // Not strictly required as exit will handle it, but good practice
        }
    }

    public IClient getClient() {
        return client;
    }
}
