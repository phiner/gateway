package phiner.de5.net.gateway.service;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 高性能外汇 Tick 生产者，使用 Redis Stream 存储实时数据。
 * 由 Lettuce 异步客户端驱动，支持自动容量修剪和多品种隔离。
 */
@Slf4j
@Service
public class ForexTickProducer {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    // 近似容量修剪参数，支持通过配置文件调整容量
    @Value("${gateway.ticks.stream.max-len:30000}")
    private int maxStreamLength;

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private RedisAsyncCommands<String, String> asyncCommands;

    private static final String STREAM_KEY_PREFIX = "gateway:ticks:stream:";

    @PostConstruct
    public void init() {
        try {
            RedisURI.Builder builder = RedisURI.builder()
                    .withHost(redisHost)
                    .withPort(redisPort);
            
            if (redisPassword != null && !redisPassword.isEmpty()) {
                builder.withPassword(redisPassword.toCharArray());
            }

            redisClient = RedisClient.create(builder.build());
            connection = redisClient.connect();
            asyncCommands = connection.async();
            log.info("ForexTickProducer 已初始化，连接至 Redis: {}:{}", redisHost, redisPort);
        } catch (Exception e) {
            log.error("ForexTickProducer 初始化失败，Tick 数据无法写入 Redis: {}", e.getMessage(), e);
        }
    }

    /**
     * 异步发送 Tick 数据到对应的品种 Stream。
     * 
     * @param symbol    交易品种名称 (如 EUR/USD)
     * @param timestamp 系统时间戳 (毫秒)
     * @param bid       买入价
     * @param ask       卖出价
     * @param bidVol    买入量
     * @param askVol    卖出量
     */
    public void sendTickAsync(String symbol, long timestamp, double bid, double ask, double bidVol, double askVol) {
        // [BLOCKER 修正] Fail-safe NPE 防御：如果连接失败，丢弃 Tick 并避免系统崩溃
        if (asyncCommands == null) {
            log.warn("Redis asyncCommands 未就绪，丢弃 Tick 写入 [{}]", symbol);
            return;
        }

        // Stream 键名包含品种名，数据体内不再包含
        String streamKey = STREAM_KEY_PREFIX + symbol;
        
        // 使用简短字段名以优化序列化和节省带宽
        Map<String, String> data = new HashMap<>(5); // 预分配容量，微优化对象分配
        data.put("t", String.valueOf(timestamp));
        data.put("b", String.valueOf(bid));
        data.put("a", String.valueOf(ask));
        data.put("bv", String.valueOf(bidVol));
        data.put("av", String.valueOf(askVol));

        // 使用 MAXLEN ~ 提升性能，避免严格修剪带来的性能损耗
        XAddArgs args = XAddArgs.args().maxlen(maxStreamLength).approximateTrimming(true);
        
        asyncCommands.xadd(streamKey, args, data)
                .exceptionally(ex -> {
                    log.error("写入 Redis Stream 失败 [{}]: {}", symbol, ex.getMessage());
                    return null;
                });
    }

    @PreDestroy
    public void shutdown() {
        log.info("ForexTickProducer 正在关闭连接...");
        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    /**
     * [MAJOR 修正] 暴露包级 Setter 以支持在单元测试中注入 Mock 命令，增强可测试性。
     */
    void setAsyncCommandsForTest(RedisAsyncCommands<String, String> asyncCommands) {
        this.asyncCommands = asyncCommands;
    }
}
