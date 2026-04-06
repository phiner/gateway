package phiner.de5.net.gateway.service;

import io.lettuce.core.XAddArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 高性能外汇 Tick 生产者，使用 Redis Stream 存储实时数据。
 * 基于 Spring 托管的 Lettuce 连接工厂，实现异步非阻塞写入、自动容量修剪。
 */
@Slf4j
@Service
public class ForexTickProducer {

    private final RedisConnectionFactory connectionFactory;

    // 近似容量修剪参数，支持通过配置文件调整容量
    @Value("${gateway.ticks.stream.max-len:30000}")
    private int maxStreamLength;

    private RedisAsyncCommands<String, String> asyncCommands;
    private XAddArgs xAddArgs;
    
    // 价格格式化：固定 5 位小数，防止科学计数法和精度丢失。使用 US 符号确保小数点为 '.'
    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("0.00000", DecimalFormatSymbols.getInstance(Locale.US));
    
    private long lastErrorLogTime = 0;
    private static final long LOG_THROTTLE_MS = 5000;
    private static final String STREAM_KEY_PREFIX = "gateway:ticks:stream:";

    public ForexTickProducer(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @PostConstruct
    public void init() {
        try {
            // 从 Spring 托管的工厂中获取 Lettuce 原生连接，复用连接池与全局配置（如 SSL、超时等）
            if (connectionFactory.getConnection() instanceof LettuceConnection lettuceConn) {
                // Lettuce 连接是线程安全的，持有其异步命令集直接执行高性能写入
                Object nativeConn = lettuceConn.getNativeConnection();
                if (nativeConn instanceof StatefulRedisConnection) {
                    @SuppressWarnings("unchecked")
                    StatefulRedisConnection<String, String> statefulConn = (StatefulRedisConnection<String, String>) nativeConn;
                    this.asyncCommands = statefulConn.async();
                }
            }
            
            // 预先创建并缓存 XAddArgs 对象，避免在高频 Tick 写入时重复创建，降低 GC 压力
            this.xAddArgs = new XAddArgs().maxlen(maxStreamLength).approximateTrimming(true);
            
            log.info("ForexTickProducer 已通过 Spring ConnectionFactory 成功初始化");
        } catch (Exception e) {
            log.error("ForexTickProducer 初始化失败: {}", e.getMessage(), e);
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
        // Fail-safe NPE 防御
        if (asyncCommands == null) {
            throttledLogWarn("Redis 异步命令集未就绪，丢弃 Tick 写入 [" + symbol + "]");
            return;
        }

        String streamKey = STREAM_KEY_PREFIX + symbol;
        
        // 使用简短字段名并采用固定精度字符串格式化价格数据
        Map<String, String> data = new HashMap<>(5);
        data.put("t", String.valueOf(timestamp));
        data.put("b", PRICE_FORMAT.format(bid));
        data.put("a", PRICE_FORMAT.format(ask));
        data.put("bv", String.valueOf(bidVol));
        data.put("av", String.valueOf(askVol));

        asyncCommands.xadd(streamKey, xAddArgs, data)
                .exceptionally(ex -> {
                    throttledLogWarn("写入 Redis Stream 失败 [" + symbol + "]: " + ex.getMessage());
                    return null;
                });
    }

    /**
     * 日志限流逻辑：防止在连接异常且有高频 Tick 涌入时导致日志爆炸 (Log Flooding)
     */
    private void throttledLogWarn(String message) {
        long now = System.currentTimeMillis();
        if (now - lastErrorLogTime > LOG_THROTTLE_MS) {
            log.warn(message);
            lastErrorLogTime = now;
        }
    }

    /**
     * 支持在单元测试中注入 Mock 命令集
     */
    void setAsyncCommandsForTest(RedisAsyncCommands<String, String> asyncCommands) {
        this.asyncCommands = asyncCommands;
    }
}
