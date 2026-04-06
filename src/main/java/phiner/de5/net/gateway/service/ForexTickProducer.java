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

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 高性能外汇 Tick 生产者，使用 Redis Stream 存储实时数据。
 * 基于 Spring 托管的 Lettuce 连接工厂，实现异步非阻塞写入、自动容量修剪。
 * 已优化为基于 byte[] 的底层模式，彻底解决编解码器冲突与 GC 频率问题。
 */
@Slf4j
@Service
public class ForexTickProducer {

    private final RedisConnectionFactory connectionFactory;

    @Value("${gateway.ticks.stream.max-len:30000}")
    private int maxStreamLength;

    @Value("${spring.data.redis.host:localhost}")
    private String activeRedisHost;

    // 切换至字节数组模式，以兼容 Spring Data Redis 的默认 ByteArrayCodec
    private RedisAsyncCommands<byte[], byte[]> asyncCommands;
    private XAddArgs xAddArgs;
    
    // 预分配常用字段键，避免在每秒数千次的 Tick 循环中产生不必要的 byte[] 分配
    private static final byte[] FIELD_T = "t".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FIELD_B = "b".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FIELD_A = "a".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FIELD_BV = "bv".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FIELD_AV = "av".getBytes(StandardCharsets.UTF_8);
    
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
        org.springframework.data.redis.connection.RedisConnection conn = null;
        try {
            log.info("ForexTickProducer 正在尝试连接 Redis: {}", activeRedisHost);
            
            // 获取一个临时连接包装器用于探测
            conn = connectionFactory.getConnection();
            
            if (conn instanceof LettuceConnection lettuceConn) {
                // Lettuce 连接底层是单路复用的，getNativeConnection() 通常返回共享的 StatefulRedisConnection
                Object nativeConn = lettuceConn.getNativeConnection();
                
                if (nativeConn instanceof RedisAsyncCommands) {
                    // 直接获取到了异步命令句柄 (如某些环境下的 RedisAsyncCommandsImpl)
                    @SuppressWarnings("unchecked")
                    RedisAsyncCommands<byte[], byte[]> commands = (RedisAsyncCommands<byte[], byte[]>) nativeConn;
                    this.asyncCommands = commands;
                } else if (nativeConn instanceof StatefulRedisConnection) {
                    // 获取到的是连接对象
                    @SuppressWarnings("unchecked")
                    StatefulRedisConnection<byte[], byte[]> statefulConn = (StatefulRedisConnection<byte[], byte[]>) nativeConn;
                    this.asyncCommands = statefulConn.async();
                } else {
                    log.error("无法从 LettuceConnection 提取异步命令句柄。实际原生类型: {}", 
                        nativeConn != null ? nativeConn.getClass().getName() : "null");
                }
            } else {
                log.error("RedisConnectionFactory 返回的连接类型不是 LettuceConnection。实际类型: {}", 
                    conn != null ? conn.getClass().getName() : "null");
            }

            if (this.asyncCommands != null) {
                // 预先创建并缓存 XAddArgs 对象，降低 GC 压力
                this.xAddArgs = new XAddArgs().maxlen(maxStreamLength).approximateTrimming(true);
                log.info("ForexTickProducer 已通过 Spring ConnectionFactory 成功初始化并建立异步字节指令集");
            } else {
                log.error("ForexTickProducer 关键状态未就绪：异步 Redis 命令句柄为 null，Tick 数据写入将被静默丢弃");
            }
        } catch (Exception e) {
            log.error("ForexTickProducer 初始化过程中发生异常: {}", e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.close(); // 释放连接包装器资源，如果是共享连接则不会关闭物理连接
                } catch (Exception e) {
                    log.debug("关闭初始化探测连接时发生异常: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 异步发送 Tick 数据到对应的品种 Stream。
     * 已优化为原生 byte[] 模式，彻底解决泛型冲突并保持极致吞吐。
     */
    public void sendTickAsync(String symbol, long timestamp, double bid, double ask, double bidVol, double askVol) {
        if (asyncCommands == null) {
            throttledLogWarn("Redis 异步命令集未就绪，丢弃 Tick [" + symbol + "]");
            return;
        }

        byte[] streamKey = (STREAM_KEY_PREFIX + symbol).getBytes(StandardCharsets.UTF_8);
        
        // 构建 Map<byte[], byte[]>。使用预分配的 Key 并通过手动转换 Value 确保编解码正确。
        Map<byte[], byte[]> data = new HashMap<>(5);
        data.put(FIELD_T, String.valueOf(timestamp).getBytes(StandardCharsets.UTF_8));
        data.put(FIELD_B, PRICE_FORMAT.format(bid).getBytes(StandardCharsets.UTF_8));
        data.put(FIELD_A, PRICE_FORMAT.format(ask).getBytes(StandardCharsets.UTF_8));
        data.put(FIELD_BV, String.valueOf(bidVol).getBytes(StandardCharsets.UTF_8));
        data.put(FIELD_AV, String.valueOf(askVol).getBytes(StandardCharsets.UTF_8));

        asyncCommands.xadd(streamKey, xAddArgs, data)
                .exceptionally(ex -> {
                    throttledLogWarn("写入 Redis Stream 失败 [" + symbol + "]: " + ex.getMessage());
                    return null;
                });
    }

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
    void setAsyncCommandsForTest(RedisAsyncCommands<byte[], byte[]> asyncCommands) {
        this.asyncCommands = asyncCommands;
    }
}
