package phiner.de5.net.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 启动配置校验器，确保所有必要的基础变量已正确加载。
 */
@Component
public class ConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(ConfigValidator.class);

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private String redisPort;

    @Value("${jforex.username}")
    private String jforexUsername;

    @Value("${jforex.password}")
    private String jforexPassword;

    @Value("${jforex.url}")
    private String jforexUrl;

    @Value("${gateway.kline.storage-limit}")
    private int klineStorageLimit;

    @Value("${gateway.heartbeat.fixed-rate}")
    private long heartbeatRate;

    private final ForexProperties forexProperties;

    public ConfigValidator(ForexProperties forexProperties) {
        this.forexProperties = forexProperties;
    }

    @PostConstruct
    public void validate() {
        log.info("正在执行启动配置严格校验...");
        
        // 校验品种列表
        if (forexProperties.getInstruments() == null || forexProperties.getInstruments().isEmpty()) {
            log.error("致命错误: 配置中缺失 FOREX_INSTRUMENTS 或列表为空！程序将退出。");
            System.exit(1);
        }

        // 校验周期列表
        if (forexProperties.getPeriods() == null || forexProperties.getPeriods().isEmpty()) {
            log.error("致命错误: 配置中缺失 FOREX_PERIODS 或列表为空！程序将退出。");
            System.exit(1);
        }
        
        if (klineStorageLimit <= 0) {
            log.error("致命错误: GATEWAY_KLINE_STORAGE_LIMIT 必须大于 0，当前值: {}", klineStorageLimit);
            System.exit(1);
        }

        if (heartbeatRate < 1000) {
            log.error("致命错误: GATEWAY_HEARTBEAT_INTERVAL 过小，建议至少 1000ms，当前值: {}", heartbeatRate);
            System.exit(1);
        }

        log.info("核心配置校验通过。已连接 Redis 目标: {}:{}", redisHost, redisPort);
        log.info("JForex 用户名: {}", jforexUsername);
    }
}
