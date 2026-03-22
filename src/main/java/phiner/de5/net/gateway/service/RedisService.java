package phiner.de5.net.gateway.service;

import com.dukascopy.api.IMessage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import phiner.de5.net.gateway.MsgpackUtil;
import phiner.de5.net.gateway.dto.AccountStatusDTO;
import phiner.de5.net.gateway.dto.BarDTO;
import phiner.de5.net.gateway.dto.ErrorDTO;
import phiner.de5.net.gateway.dto.GatewayStatusDTO;
import phiner.de5.net.gateway.dto.InstrumentInfoDTO;
import phiner.de5.net.gateway.dto.OrderEventDTO;
import phiner.de5.net.gateway.dto.OrderHistoryDTO;
import phiner.de5.net.gateway.dto.OrdersHistoryResponseDTO;
import phiner.de5.net.gateway.dto.PositionDTO;
import phiner.de5.net.gateway.dto.TickDTO;
import phiner.de5.net.gateway.util.PeriodUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RedisService {

  @Value("${gateway.kline.storage-limit}")
  private int klineStorageLimit;

  private static final String KLINE_KEY_PREFIX = "gateway:kline";
  private static final String POSITIONS_HASH_KEY = "gateway:positions:active";
  private static final String POSITIONS_UPDATED_CHANNEL = "gateway:positions:updated";
  private static final String HISTORY_HASH_KEY = "gateway:orders:history";
  private static final String HISTORY_UPDATED_CHANNEL = "gateway:orders:history:updated";
  private static final String HISTORY_COVERAGE_HASH_KEY = "gateway:history:coverage";

  private final RedisTemplate<String, byte[]> redisTemplateBytes;
  private final RedisTemplate<String, String> redisTemplateString;

  public RedisService(
      @Qualifier("redisTemplateBytes") @NonNull RedisTemplate<String, byte[]> redisTemplateBytes,
      @Qualifier("redisTemplateString") @NonNull RedisTemplate<String, String> redisTemplateString) {
    this.redisTemplateBytes = redisTemplateBytes;
    this.redisTemplateString = redisTemplateString;
  }

  public void addBarToKLine(@NonNull BarDTO bar) {
    if (bar.getInstrument() == null || bar.getPeriod() == null) {
      log.error("RedisService: Cannot process a bar with a null instrument/period.");
      return;
    }

    String redisKey =
        String.format(
            "%s:%s:%s",
            KLINE_KEY_PREFIX, bar.getInstrument(), bar.getPeriod());
    byte[] barData = MsgpackUtil.encode(bar);

    if (barData == null) {
      log.error("RedisService: Failed to serialize BarDTO to MessagePack. The resulting data is null.");
      return;
    }

    try {
      redisTemplateBytes.opsForList().leftPush(Objects.requireNonNull(redisKey), barData);
      redisTemplateBytes.opsForList().trim(Objects.requireNonNull(redisKey), 0, klineStorageLimit - 1);
    } catch (Exception e) {
      log.warn("RedisService: Failed to write bar to key {}: {}", redisKey, e.getMessage());
    }
  }

  public List<BarDTO> getKLine(@NonNull String instrument, @NonNull String period) {
    String redisKey =
        String.format("%s:%s:%s", KLINE_KEY_PREFIX, instrument, period);
    try {
      List<byte[]> barDataList =
          redisTemplateBytes.opsForList().range(Objects.requireNonNull(redisKey), 0, -1);
      if (barDataList == null) {
        return Collections.emptyList();
      }
      return barDataList.stream()
          .map(data -> MsgpackUtil.decode(data, BarDTO.class))
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.error("RedisService: Error while reading K-line from Redis for key '{}': {}", redisKey, e.getMessage());
      return Collections.emptyList();
    }
  }

  private <T> void publishToChannel(String channel, T data) {
    try {
      byte[] encoded = MsgpackUtil.encode(data);
      if (encoded != null) {
        redisTemplateBytes.convertAndSend(channel, encoded);
      }
    } catch (Exception e) {
      log.warn("Failed to publish to channel {}: {}", channel, e.getMessage());
    }
  }

  private void publishStringToChannel(String channel, String message) {
    try {
      redisTemplateString.convertAndSend(channel, message);
    } catch (Exception e) {
      log.warn("Failed to publish string to channel {}: {}", channel, e.getMessage());
    }
  }

  public void publishTick(@NonNull TickDTO tick) {
    if (tick.getInstrument() == null) {
      log.error("RedisService: Cannot publish tick with null instrument.");
      return;
    }
    String channel = "gateway:tick:" + tick.getInstrument();
    publishToChannel(channel, tick);
  }

  public void publishBar(@NonNull BarDTO bar) {
    if (bar.getInstrument() == null || bar.getPeriod() == null) {
      log.error("RedisService: Cannot publish bar with null instrument/period.");
      return;
    }
    String channel = String.format("gateway:kline:%s:%s", bar.getInstrument(), bar.getPeriod());
    publishToChannel(channel, bar);
  }

  public void publishOrderEvent(@NonNull IMessage message) {
    String channel = "gateway:order:event";
    OrderEventDTO eventDTO = new OrderEventDTO(message);
    publishToChannel(channel, eventDTO);
  }

  public void publishAccountStatus(double balance, double equity, double baseEquity, double margin, double unrealizedPL) {
    String channel = "gateway:account:status";
    AccountStatusDTO accountStatus = new AccountStatusDTO(balance, equity, baseEquity, margin, unrealizedPL);
    publishToChannel(channel, accountStatus);
  }

  public void publishError(@NonNull String errorMessage) {
    publishStringToChannel("gateway:error", errorMessage);
  }

  public void publishStructuredError(@NonNull ErrorDTO errorDTO) {
    String channel = "gateway:error:structured";
    publishToChannel(channel, errorDTO);
    log.info("Published structured error: {} - {}", errorDTO.getCode(), errorDTO.getMessage());
  }

  public void publishInfo(@NonNull String infoMessage) {
    publishStringToChannel("gateway:info", infoMessage);
  }

  public void publishGatewayStatus(@NonNull GatewayStatusDTO statusDTO) {
    publishToChannel("gateway:status", statusDTO);
  }

  public void publishInstrumentInfo(@NonNull InstrumentInfoDTO info, @NonNull String requestId) {
    String channel = String.format("gateway:info:instrument:response:%s", requestId);
    publishToChannel(channel, info);
  }

    /**
     * @deprecated Use refreshPositionsHash instead. This request-response method is being phased out in favor of the Hash-based state model.
     */
    @Deprecated
    public void publishPositions(@NonNull phiner.de5.net.gateway.dto.PositionListResponseDTO response, @NonNull String requestId) {
        // No-op. Clients should use the baseline Hash: gateway:positions:active
    }

    // 从配置文件中读取 Redis 主机地址，默认值为 127.0.0.1
    @Value("${spring.data.redis.host:127.0.0.1}")
    private String redisHost;

    // 从配置文件中读取 Redis 端口号，默认值为 6379
    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    public void testConnection() {
        try {
            redisTemplateBytes.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> connection.ping());
        } catch (Exception e) {
            // 组装包含详细地址信息的错误提示并抛出
            String errorMsg = String.format("Redis connection check failed at address [%s:%s]. Unable to connect to Redis: %s", redisHost, redisPort, e.getMessage());
            throw new RuntimeException(errorMsg, e);
        }
    }

    public void saveConfigInstruments(@NonNull List<String> instruments) {
        String key = "gateway:config:instruments";
        try {
            redisTemplateString.delete(key);
            if (!instruments.isEmpty()) {
                redisTemplateString.opsForSet().add(key, instruments.toArray(new String[0]));
                log.info("Saved {} instruments to Redis set: {}", instruments.size(), key);
            }
        } catch (Exception e) {
            log.error("Failed to save config instruments to Redis: {}", e.getMessage(), e);
        }
    }

    public void saveConfigPeriods(@NonNull List<String> periods) {
        String key = "gateway:config:periods";
        try {
            redisTemplateString.delete(key);
            if (!periods.isEmpty()) {
                List<String> formattedPeriods = periods.stream()
                        .map(PeriodUtil::format)
                        .collect(Collectors.toList());
                redisTemplateString.opsForSet().add(key, formattedPeriods.toArray(new String[0]));
                log.info("Saved {} periods to Redis set: {}", formattedPeriods.size(), key);
            }
        } catch (Exception e) {
            log.error("Failed to save config periods to Redis: {}", e.getMessage(), e);
        }
    }

    public void saveInstrumentInfo(@NonNull InstrumentInfoDTO info) {
        String key = "gateway:config:instrument_info";
        try {
            byte[] data = MsgpackUtil.encode(info);
            if (data != null) {
                redisTemplateBytes.opsForHash().put(key, info.getName(), data);
                log.info("Saved instrument info for {} to Redis hash: {}", info.getName(), key);
            }
        } catch (Exception e) {
            log.error("Failed to save instrument info for {} to Redis: {}", info.getName(), e.getMessage(), e);
        }
    }

    public void refreshPositionsHash(@NonNull List<PositionDTO> positions) {
        try {
            redisTemplateBytes.delete(POSITIONS_HASH_KEY);
            if (!positions.isEmpty()) {
                for (PositionDTO pos : positions) {
                    byte[] data = MsgpackUtil.encode(pos);
                    if (data != null) {
                        redisTemplateBytes.opsForHash().put(POSITIONS_HASH_KEY, pos.getDealId(), data);
                    }
                }
                log.info("Refreshed positions hash with {} positions", positions.size());
            } else {
                log.info("Refreshed positions hash (empty)");
            }
            notifyPositionsUpdated();
        } catch (Exception e) {
            log.error("Failed to refresh positions hash: {}", e.getMessage(), e);
        }
    }

    public void notifyPositionsUpdated() {
        try {
            String message = String.valueOf(System.currentTimeMillis());
            redisTemplateString.convertAndSend(POSITIONS_UPDATED_CHANNEL, message);
        } catch (Exception e) {
            log.warn("Failed to send positions updated notification: {}", e.getMessage());
        }
    }

    public void updateHistoryHash(@NonNull List<OrderHistoryDTO> orders) {
        try {
            Map<String, byte[]> map = new HashMap<>();
            for (OrderHistoryDTO order : orders) {
                byte[] data = MsgpackUtil.encode(order);
                if (data != null) {
                    map.put(order.getDealId(), data);
                }
            }
            if (!map.isEmpty()) {
                redisTemplateBytes.opsForHash().putAll(HISTORY_HASH_KEY, map);
                log.info("Updated history hash with {} orders", map.size());
            }
        } catch (Exception e) {
            log.error("Failed to update history hash: {}", e.getMessage());
        }
    }

    public void notifyHistoryUpdated(@NonNull String instrument) {
        try {
            // Notification payload can be the instrument name to let clients know which product's history grew
            redisTemplateString.convertAndSend(HISTORY_UPDATED_CHANNEL, instrument);
        } catch (Exception e) {
            log.warn("Failed to send history updated notification: {}", e.getMessage());
        }
    }

    /**
     * 获取指定品种的历史记录覆盖范围 [min, max]
     */
    public long[] getHistoryCoverage(@NonNull String instrument) {
        try {
            Object data = redisTemplateBytes.opsForHash().get(HISTORY_COVERAGE_HASH_KEY, instrument);
            if (data instanceof byte[]) {
                String s = new String((byte[]) data);
                String[] parts = s.split(",");
                if (parts.length == 2) {
                    return new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])};
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get history coverage for {}: {}", instrument, e.getMessage());
        }
        return null;
    }

    /**
     * 更新指定品种的历史记录覆盖范围
     */
    public void updateHistoryCoverage(@NonNull String instrument, long min, long max) {
        try {
            long[] current = getHistoryCoverage(instrument);
            long newMin = (current == null) ? min : Math.min(current[0], min);
            long newMax = (current == null) ? max : Math.max(current[1], max);
            
            String val = newMin + "," + newMax;
            redisTemplateBytes.opsForHash().put(HISTORY_COVERAGE_HASH_KEY, instrument, val.getBytes());
        } catch (Exception e) {
            log.error("Failed to update history coverage for {}: {}", instrument, e.getMessage());
        }
    }
}
