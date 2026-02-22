package phiner.de5.net.gateway.service;

import com.dukascopy.api.IMessage;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import phiner.de5.net.gateway.MsgpackDecoder;
import phiner.de5.net.gateway.MsgpackEncoder;
import phiner.de5.net.gateway.dto.AccountStatusDTO;
import phiner.de5.net.gateway.dto.BarDTO;
import phiner.de5.net.gateway.dto.GatewayStatusDTO;
import phiner.de5.net.gateway.dto.InstrumentInfoDTO;
import phiner.de5.net.gateway.dto.OrderEventDTO;
import phiner.de5.net.gateway.dto.TickDTO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RedisService {

  @Value("${gateway.kline.storage-limit}")
  private int klineStorageLimit;

  private static final String KLINE_KEY_PREFIX = "kline";

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
    byte[] barData = MsgpackEncoder.encode(bar);

    if (barData == null) {
      log.error("RedisService: Failed to serialize BarDTO to MessagePack. The resulting data is null.");
      return;
    }

    try {
      log.info("Writing bar to Redis list: {} (size: {} bytes)", redisKey, barData.length);
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
          .map(data -> MsgpackDecoder.decode(data, BarDTO.class))
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.error("RedisService: Error while reading K-line from Redis for key '{}': {}", redisKey, e.getMessage());
      return Collections.emptyList();
    }
  }

  public void publishTick(@NonNull TickDTO tick) {
    if (tick.getInstrument() == null) {
      log.error("RedisService: Cannot publish tick with null instrument.");
      return;
    }
    String channel = "tick:" + tick.getInstrument();
    try {
      byte[] data = MsgpackEncoder.encode(tick);
      if (data != null) {
        redisTemplateBytes.convertAndSend(Objects.requireNonNull(channel), data);
      }
    } catch (Exception e) {
      log.warn("Failed to publish tick on channel {}: {}", channel, e.getMessage());
    }
  }

  public void publishBar(@NonNull BarDTO bar) {
    if (bar.getInstrument() == null || bar.getPeriod() == null) {
      log.error("RedisService: Cannot publish bar with null instrument/period.");
      return;
    }
    String channel = String.format("kline:%s:%s", bar.getInstrument(), bar.getPeriod());
    try {
      byte[] data = MsgpackEncoder.encode(bar);
      if (data != null) {
        redisTemplateBytes.convertAndSend(Objects.requireNonNull(channel), data);
      }
    } catch (Exception e) {
      log.warn("Failed to publish bar on channel {}: {}", channel, e.getMessage());
    }
  }

  public void publishOrderEvent(@NonNull IMessage message) {
    String channel = "order:event";
    try {
      OrderEventDTO eventDTO = new OrderEventDTO(message);
      byte[] data = MsgpackEncoder.encode(eventDTO);

      if (data == null) {
        log.error("RedisService: Failed to serialize OrderEventDTO. MessagePack data is null.");
        return;
      }

      redisTemplateBytes.convertAndSend(Objects.requireNonNull(channel), data);
    } catch (Exception e) {
      log.warn("Failed to publish order event: {}", e.getMessage());
    }
  }

  public void publishAccountStatus(double balance, double equity) {
    String channel = "account:status";
    try {
      AccountStatusDTO accountStatus = new AccountStatusDTO(balance, equity);
      byte[] data = MsgpackEncoder.encode(accountStatus);

      if (data != null) {
        redisTemplateBytes.convertAndSend(channel, data);
      }
    } catch (Exception e) {
      log.warn("Failed to publish account status: {}", e.getMessage());
    }
  }

  public void publishError(@NonNull String errorMessage) {
    String channel = "gateway:error";
    try {
      redisTemplateString.convertAndSend(Objects.requireNonNull(channel), errorMessage);
    } catch (Exception e) {
      log.warn("Failed to publish error message to {}: {}", channel, e.getMessage());
    }
  }

  public void publishInfo(@NonNull String infoMessage) {
    String channel = "gateway:info";
    try {
      redisTemplateString.convertAndSend(Objects.requireNonNull(channel), infoMessage);
    } catch (Exception e) {
      log.warn("Failed to publish info message to {}: {}", channel, e.getMessage());
    }
  }

    public void publishGatewayStatus(@NonNull GatewayStatusDTO statusDTO) {
        String channel = "gateway:status";
        try {
            byte[] data = MsgpackEncoder.encode(statusDTO);
            if (data != null) {
                redisTemplateBytes.convertAndSend(Objects.requireNonNull(channel), data);
            }
        } catch (Exception e) {
            log.warn("Failed to publish gateway status: {}", e.getMessage());
        }
    }

    public void publishInstrumentInfo(@NonNull InstrumentInfoDTO info, @NonNull String requestId) {
        String channel = String.format("info:instrument:response:%s", requestId);
        try {
            byte[] data = MsgpackEncoder.encode(info);
            if (data != null) {
                redisTemplateBytes.convertAndSend(Objects.requireNonNull(channel), data);
            }
        } catch (Exception e) {
            log.warn("Failed to publish instrument info for request {}: {}", requestId, e.getMessage());
        }
    }

    public void testConnection() {
        try {
            redisTemplateBytes.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> connection.ping());
        } catch (Exception e) {
            throw new RuntimeException("Redis connection check failed: " + e.getMessage(), e);
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
                redisTemplateString.opsForSet().add(key, periods.toArray(new String[0]));
                log.info("Saved {} periods to Redis set: {}", periods.size(), key);
            }
        } catch (Exception e) {
            log.error("Failed to save config periods to Redis: {}", e.getMessage(), e);
        }
    }

    public void saveInstrumentInfo(@NonNull InstrumentInfoDTO info) {
        String key = "gateway:config:instrument_info";
        try {
            byte[] data = MsgpackEncoder.encode(info);
            if (data != null) {
                redisTemplateBytes.opsForHash().put(key, info.getName(), data);
                log.info("Saved instrument info for {} to Redis hash: {}", info.getName(), key);
            }
        } catch (Exception e) {
            log.error("Failed to save instrument info for {} to Redis: {}", info.getName(), e.getMessage(), e);
        }
    }
}
