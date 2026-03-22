package phiner.de5.net.gateway.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一的周期格式化工具类，将 JForex 各种形式的周期字符串转换为短格式（如 5m, 1h, 1d）。
 */
public class PeriodUtil {

    private static final Logger log = LoggerFactory.getLogger(PeriodUtil.class);

    private static final Map<String, String> MAPPING = new HashMap<>();

    static {
        // JForex Enum Names (常量名)
        MAPPING.put("ONE_MIN", "1m");
        MAPPING.put("FIVE_MINS", "5m");
        MAPPING.put("FIFTEEN_MINS", "15m");
        MAPPING.put("THIRTY_MINS", "30m");
        MAPPING.put("ONE_HOUR", "1h");
        MAPPING.put("FOUR_HOURS", "4h");
        MAPPING.put("DAILY", "1d");
        MAPPING.put("WEEKLY", "1w");
        MAPPING.put("MONTHLY", "1M");

        // JForex toString() Names (显示名)
        MAPPING.put("1 Min", "1m");
        MAPPING.put("5 Mins", "5m");
        MAPPING.put("15 Mins", "15m");
        MAPPING.put("30 Mins", "30m");
        MAPPING.put("1 Hour", "1h");
        MAPPING.put("4 Hours", "4h");
        MAPPING.put("Daily", "1d");
        MAPPING.put("Weekly", "1w");
        MAPPING.put("Monthly", "1M");
    }

    /**
     * 将输入的周期字符串转换为短格式。如果无法匹配，则返回原字符串。
     *
     * @param period 原始周期字符串 (例如 "FIVE_MINS" 或 "5 Mins")
     * @return 转换后的短格式 (例如 "5m")
     */
    public static String format(String period) {
        if (period == null || period.isEmpty()) {
            return period;
        }
        
        String result = MAPPING.get(period);
        if (result != null) {
            return result;
        }

        // 容错处理：转换常见格式
        String cleaned = period.trim();
        if (cleaned.endsWith(" Mins")) {
            return cleaned.replace(" Mins", "m");
        } else if (cleaned.endsWith(" Min")) {
            return cleaned.replace(" Min", "m");
        } else if (cleaned.endsWith(" Hours")) {
            return cleaned.replace(" Hours", "h");
        } else if (cleaned.endsWith(" Hour")) {
            return cleaned.replace(" Hour", "h");
        }
        
        log.debug("Unmapped period string: '{}', returning as is.", period);
        return period;
    }
}
