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
 * [重构优化] 切换至原生 byte[] 模式以支撑最高吞吐，并预分配静态字段键以消除 GC 压力。
 */
@Slf4j
@Service
public class ForexTickProducer {

    private final RedisConnectionFactory connectionFactory;

    @Value("${gateway.ticks.stream.max-len:30000}")
    private int maxStreamLength;

    @Value("${spring.data.redis.host:localhost}")
    private String activeRedisHost;

    private RedisAsyncCommands<byte[], byte[]> asyncCommands;
    private XAddArgs xAddArgs;
    
    // 价格格式化：5 位小数，US 符号
    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("0.00000", DecimalFormatSymbols.getInstance(Locale.US));
    
    private long lastErrorLogTime = 0;
    private static final long LOG_THROTTLE_MS = 5000;
    private static final String STREAM_KEY_PREFIX = "gateway:ticks:stream:";

    // 预分配字段键字节数组，消除高频写入时的对象分配
    private static final byte[] FIELD_T = "t".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FIELD_B = "b".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FIELD_A = "a".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FIELD_BV = "bv".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FIELD_AV = "av".getBytes(StandardCharsets.UTF_8);

    public ForexTickProducer(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @PostConstruct
    public void init() {
        try {
            log.info("ForexTickProducer 正在尝试连接 Redis: {}", activeRedisHost);
            
            if (connectionFactory.getConnection() instanceof LettuceConnection lettuceConn) {
                Object nativeConn = lettuceConn.getNativeConnection();
                // 必须适配 byte[] 类型连接以匹配高性能写入指令
                if (nativeConn instanceof StatefulRedisConnection<?, ?> statefulConn) {
                    @SuppressWarnings("unchecked")
                    StatefulRedisConnection<byte[], byte[]> byteConn = (StatefulRedisConnection<byte[], byte[]>) nativeConn;
                    this.asyncCommands = byteConn.async();
                }
            }
            
            this.xAddArgs = new XAddArgs().maxlen(maxStreamLength).approximateTrimming(true);
            log.info("ForexTickProducer 已通过 Spring ConnectionFactory 成功初始化并建立异步字节指令集");
        } catch (Exception e) {
            log.error("ForexTickProducer 初始化失败: {}", e.getMessage(), e);
        }
    }

    public void sendTickAsync(String symbol, long timestamp, double bid, double ask, double bidVol, double askVol) {
        if (asyncCommands == null) {
            throttledLogWarn("Redis 异步命令集未就绪，丢弃 Tick [" + symbol + "]");
            return;
        }

        byte[] streamKey = (STREAM_KEY_PREFIX + symbol).getBytes(StandardCharsets.UTF_8);
        
        // 构造数据 Map
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
