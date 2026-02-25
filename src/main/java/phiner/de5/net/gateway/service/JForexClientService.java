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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class JForexClientService {

    private final JForexProperties jForexProperties;
    private final TradingStrategy tradingStrategy;
    private final RedisService redisService;
    private IClient client;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> periodicConnectTask;
    private ScheduledFuture<?> delayedReconnectTask;

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
            log.error("PRECONDITION FAILED: Redis must be connected before starting JForex gateway.", e);
            System.exit(1);
        }
    }

    private void setupListener() {
        client.setSystemListener(new ISystemListener() {
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
                cancelReconnectionTasks();
                startStrategy();
            }

            @Override
            public void onDisconnect() {
                log.warn("Disconnected from JForex server");
                scheduleReconnection();
            }
        });
    }

    private void scheduleReconnection() {
        // 取消之前的延迟重连任务，避免重复
        if (delayedReconnectTask != null && !delayedReconnectTask.isDone()) {
            delayedReconnectTask.cancel(false);
        }

        log.info("Scheduling light reconnect in 15 seconds...");
        delayedReconnectTask = scheduler.schedule(() -> {
            try {
                log.info("Attempting light reconnect...");
                client.reconnect();
            } catch (Exception e) {
                log.error("Light reconnection attempt failed: {}", e.getMessage());
            }
        }, 15, TimeUnit.SECONDS);

        // 如果周期性全连接任务未运行，则启动之（每3分钟尝试一次全连接）
        if (periodicConnectTask == null || periodicConnectTask.isDone()) {
            log.info("Starting periodic full connect task (every 3 minutes)...");
            periodicConnectTask = scheduler.scheduleAtFixedRate(() -> {
                log.info("Periodic full connect task triggered.");
                connect();
            }, 3, 3, TimeUnit.MINUTES);
        }
    }

    private void cancelReconnectionTasks() {
        if (delayedReconnectTask != null) {
            delayedReconnectTask.cancel(false);
            delayedReconnectTask = null;
        }
        if (periodicConnectTask != null) {
            log.info("Cancelling periodic full connect task.");
            periodicConnectTask.cancel(false);
            periodicConnectTask = null;
        }
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
        log.info("Shutting down JForexClientService scheduler...");
        scheduler.shutdownNow();
        if (client != null) {
            log.info("Shutting down JForex client...");
            try {
                client.stopStrategy(0);
                client.disconnect();
            } catch (Exception e) {
                log.error("Error during JForex client shutdown: {}", e.getMessage());
            }
        }
    }

    public IClient getClient() {
        return client;
    }
}
